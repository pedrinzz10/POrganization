package com.porganization.integrations.google;

import com.porganization.common.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OAuth 2.0 (authorization code) com o Google para o escopo calendar.events.
 * <ul>
 * <li>O state leva o usuário e a validade e é assinado (HMAC): o callback, que chega sem JWT
 * (redirecionamento do navegador), só aceita um state que a própria API gerou há menos de 10 minutos.</li>
 * <li>O refresh token é guardado cifrado (TokenCrypto) e nunca sai em resposta da API.</li>
 * <li>accessToken() renova o token de acesso quando está para vencer (usado pela sincronização, I07/I08).</li>
 * </ul>
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    static final String SCOPE = "https://www.googleapis.com/auth/calendar.events openid email";
    static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);

    private final GoogleProperties google;
    private final TokenCrypto crypto;
    private final GoogleConnectionRepository connections;
    private final RestClient http;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public GoogleOAuthService(GoogleProperties google, TokenCrypto crypto, GoogleConnectionRepository connections,
            RestClient googleRestClient, ObjectMapper json, Clock clock) {
        this.google = google;
        this.crypto = crypto;
        this.connections = connections;
        this.http = googleRestClient;
        this.json = json;
        this.clock = clock;
    }

    public record Status(boolean available, boolean connected, String email, String calendarId, OffsetDateTime connectedAt) {
    }

    /** Integração configurada no servidor (credenciais do Google e chave dos tokens)? */
    public boolean available() {
        return google.configured() && crypto.configured();
    }

    /** URL da tela de consentimento do Google para este usuário. */
    public String authUrl(UUID userId) {
        requireAvailable();
        return UriComponentsBuilder.fromUriString(google.authUri())
                .queryParam("client_id", google.clientId())
                .queryParam("redirect_uri", google.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                // offline + consent: o Google devolve refresh token mesmo em reconexões
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state(userId))
                .encode()
                .build()
                .toUriString();
    }

    /** "base64url(userId.expira.nonce).assinatura" */
    String state(UUID userId) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String payload = "%s.%d.%s".formatted(userId, clock.instant().plus(STATE_TTL).getEpochSecond(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + crypto.sign(payload);
    }

    /** O usuário dono do state; state adulterado, expirado ou malformado → 400. */
    UUID verifyState(String state) {
        try {
            int dot = state.lastIndexOf('.');
            String payload = new String(Base64.getUrlDecoder().decode(state.substring(0, dot)), StandardCharsets.UTF_8);
            if (!crypto.verify(payload, state.substring(dot + 1))) {
                throw new IllegalArgumentException("assinatura");
            }
            String[] parts = payload.split("\\.");
            if (Instant.ofEpochSecond(Long.parseLong(parts[1])).isBefore(clock.instant())) {
                throw new IllegalArgumentException("expirado");
            }
            return UUID.fromString(parts[0]);
        } catch (RuntimeException e) {
            throw new InvalidRequestException("state", "state inválido ou expirado; conecte de novo pelas Configurações");
        }
    }

    /** Callback: valida o state, troca o code por tokens e guarda a conexão. Devolve o usuário conectado. */
    @Transactional
    public UUID connect(String code, String state) {
        UUID userId = verifyState(state);
        requireAvailable();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", google.clientId());
        form.add("client_secret", google.clientSecret());
        form.add("redirect_uri", google.redirectUri());
        form.add("grant_type", "authorization_code");
        JsonNode tokens = postForm(google.tokenUri(), form);

        String refreshToken = text(tokens, "refresh_token");
        if (refreshToken == null) {
            throw new IllegalStateException("o Google não devolveu refresh token");
        }
        GoogleConnection connection = connections.findById(userId).orElseGet(() -> new GoogleConnection(userId));
        connection.connected(emailFrom(text(tokens, "id_token")), crypto.encrypt(refreshToken), text(tokens, "scope"));
        storeAccessToken(connection, tokens);
        connections.save(connection);
        return userId;
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId) {
        return connections.findById(userId)
                .map(c -> new Status(available(), true, c.getGoogleEmail(), c.getCalendarId(), c.getCreatedAt()))
                .orElseGet(() -> new Status(available(), false, null, null, null));
    }

    /** Revoga o token no Google (melhor esforço) e apaga a conexão. */
    @Transactional
    public void disconnect(UUID userId) {
        connections.findById(userId).ifPresent(connection -> {
            try {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("token", crypto.decrypt(connection.getRefreshTokenEnc()));
                postForm(google.revokeUri(), form);
            } catch (RuntimeException e) {
                // Token já revogado pelo usuário no Google (400) ou Google fora do ar: a conexão sai mesmo assim
                log.warn("Falha ao revogar o token do Google do usuário {}: {}", userId, e.getMessage());
            }
            connections.delete(connection);
        });
    }

    /** Token de acesso válido; renova com o refresh token quando falta menos de 1 minuto para vencer. */
    @Transactional
    public Optional<String> accessToken(UUID userId) {
        Optional<GoogleConnection> found = connections.findById(userId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        GoogleConnection connection = found.get();
        if (connection.getAccessTokenEnc() != null && connection.getAccessExpiresAt() != null
                && connection.getAccessExpiresAt().isAfter(clock.instant().plus(EXPIRY_MARGIN))) {
            return Optional.of(crypto.decrypt(connection.getAccessTokenEnc()));
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", google.clientId());
        form.add("client_secret", google.clientSecret());
        form.add("refresh_token", crypto.decrypt(connection.getRefreshTokenEnc()));
        form.add("grant_type", "refresh_token");
        JsonNode tokens = postForm(google.tokenUri(), form);
        storeAccessToken(connection, tokens);
        connections.save(connection);
        return Optional.of(text(tokens, "access_token"));
    }

    private void storeAccessToken(GoogleConnection connection, JsonNode tokens) {
        String access = text(tokens, "access_token");
        long expiresIn = tokens.path("expires_in").asLong(3600);
        connection.accessToken(access == null ? null : crypto.encrypt(access), clock.instant().plusSeconds(expiresIn));
    }

    private JsonNode postForm(String uri, MultiValueMap<String, String> form) {
        try {
            String body = http.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(String.class);
            return json.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (RestClientException e) {
            throw new IllegalStateException("Google respondeu com erro: " + e.getMessage(), e);
        }
    }

    /** O e-mail da conta, do id_token que veio direto do Google por TLS (só para mostrar na tela). */
    private String emailFrom(String idToken) {
        if (idToken == null || idToken.split("\\.").length < 2) {
            return null;
        }
        try {
            JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(idToken.split("\\.")[1]));
            return text(claims, "email");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("Integração com o Google não configurada no servidor");
        }
    }
}
