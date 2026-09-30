package com.porganization.notifications.push;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.porganization.notifications.Notification;
import com.porganization.support.IntegrationTest;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** O PushChannel de verdade contra um push service de mentira (WireMock). */
class PushChannelIT extends IntegrationTest {

    private static final WireMockServer pushService = new WireMockServer(wireMockConfig().dynamicPort());
    private static final KeyPair VAPID = parDeChaves();

    static {
        pushService.start();
    }

    @AfterAll
    static void pararPushService() {
        pushService.stop();
    }

    @DynamicPropertySource
    static void vapid(DynamicPropertyRegistry registry) {
        registry.add("VAPID_PUBLIC_KEY", () -> publica(VAPID));
        registry.add("VAPID_PRIVATE_KEY", () -> privada(VAPID));
        registry.add("VAPID_SUBJECT", () -> "mailto:teste@porganization.test");
    }

    @Autowired
    private PushChannel push;

    @Autowired
    private PushSubscriptionRepository subscriptions;

    @Autowired
    private ObjectMapper json;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void limpar() {
        pushService.resetAll();
    }

    /** Assinatura como a de um navegador: chave P-256 e segredo de 16 bytes, em base64url. */
    private PushSubscription navegador(String caminho) {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return subscriptions.save(new PushSubscription(userId, pushService.baseUrl() + caminho, publica(parDeChaves()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(auth), "Chrome de teste"));
    }

    private static Notification lembrete(UUID userId) {
        return new Notification(userId, Notification.Kind.REMINDER, "Lembrete: Dentista às 15:00",
                List.of("quinta-feira, 1 de outubro às 15:00"), "/hoje");
    }

    // I03 T2 (CA2)
    @Test
    void assinaturaQueOPushServiceDaComoExpirada410EApagada() throws Exception {
        pushService.stubFor(post("/push/expirada").willReturn(aResponse().withStatus(410)));
        PushSubscription expirada = navegador("/push/expirada");

        push.send(lembrete(userId));

        pushService.verify(postRequestedFor(urlEqualTo("/push/expirada")));
        assertThat(subscriptions.findByEndpoint(expirada.getEndpoint())).isEmpty();
    }

    @Test
    void enviaCifradoComVapidEMantemAAssinaturaQueFuncionou() throws Exception {
        pushService.stubFor(post("/push/ok").willReturn(aResponse().withStatus(201)));
        PushSubscription ativa = navegador("/push/ok");

        push.send(lembrete(userId));

        pushService.verify(postRequestedFor(urlEqualTo("/push/ok"))
                .withHeader("Content-Encoding", equalTo("aes128gcm"))
                .withHeader("TTL", com.github.tomakehurst.wiremock.client.WireMock.matching("\\d+"))
                .withHeader("Authorization", com.github.tomakehurst.wiremock.client.WireMock.matching("vapid t=.+, k=.+")));
        assertThat(subscriptions.findByEndpoint(ativa.getEndpoint())).isPresent();
    }

    @Test
    void erroDoPushServiceSemNenhumaEntregaViraFalha() {
        pushService.stubFor(post("/push/erro").willReturn(aResponse().withStatus(500).withBody("fora do ar")));
        navegador("/push/erro");

        assertThatThrownBy(() -> push.send(lembrete(userId))).hasMessageContaining("500");
    }

    @Test
    void usuarioSemNavegadorInscritoNaoFalha() throws Exception {
        push.send(lembrete(UUID.randomUUID()));

        assertThat(pushService.getAllServeEvents()).isEmpty();
    }

    // I03 CA3: o service worker do Angular abre ou foca o app na url do aviso
    @Test
    void payloadNoFormatoDoServiceWorkerDoAngularAbreATelaHoje() {
        JsonNode body = json.readTree(push.payload(lembrete(userId)));

        assertThat(body.at("/notification/title").asString()).isEqualTo("Lembrete: Dentista às 15:00");
        assertThat(body.at("/notification/body").asString()).isEqualTo("quinta-feira, 1 de outubro às 15:00");
        assertThat(body.at("/notification/data/onActionClick/default/operation").asString()).isEqualTo("navigateLastFocusedOrOpen");
        assertThat(body.at("/notification/data/onActionClick/default/url").asString()).isEqualTo("/hoje");
    }

    @Test
    void chavePublicaVapidParaONavegadorEDesinscrever() throws Exception {
        mockMvc.perform(get("/api/push/vapid-public-key").with(usuario(userId)))
                .andExpect(jsonPath("$.publicKey").value(publica(VAPID)));

        PushSubscription minha = navegador("/push/minha");
        mockMvc.perform(delete("/api/push/subscriptions").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + minha.getEndpoint() + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(minha.getEndpoint())).isEmpty();
    }

    // ---------- chaves P-256 em base64url, como o navegador e o `web-push generate-vapid-keys` ----------

    static KeyPair parDeChaves() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Ponto não comprimido: 0x04 || X || Y (65 bytes). */
    static String publica(KeyPair pair) {
        ECPublicKey key = (ECPublicKey) pair.getPublic();
        byte[] point = new byte[65];
        point[0] = 4;
        System.arraycopy(bytes32(key.getW().getAffineX()), 0, point, 1, 32);
        System.arraycopy(bytes32(key.getW().getAffineY()), 0, point, 33, 32);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(point);
    }

    static String privada(KeyPair pair) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes32(((ECPrivateKey) pair.getPrivate()).getS()));
    }

    private static byte[] bytes32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }
}
