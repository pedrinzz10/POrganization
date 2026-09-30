package com.porganization.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Faz o papel do Supabase Auth nos testes: publica um JWKS local com uma chave EC P-256
 * (mesmo algoritmo das JWT Signing Keys do Supabase) e assina tokens com ela.
 * O JwtDecoder real da aplicação aponta para este JWKS (ver IntegrationTest).
 */
public final class TestJwks {

    public static final String ISSUER = "https://projeto-teste.supabase.co/auth/v1";
    public static final String AUDIENCE = "authenticated";

    private static final ECKey KEY = generateKey("chave-de-teste");
    private static final HttpServer SERVER = startServer();

    private TestJwks() {
    }

    public static String jwksUri() {
        return "http://localhost:" + SERVER.getAddress().getPort() + "/auth/v1/.well-known/jwks.json";
    }

    /** Token válido como o Supabase emitiria para o usuário. */
    public static String token(UUID userId, String email) {
        return builder(userId).claim("email", email).sign();
    }

    public static Builder builder(UUID userId) {
        return new Builder(userId);
    }

    public static final class Builder {

        private final JWTClaimsSet.Builder claims;
        private ECKey signingKey = KEY;

        private Builder(UUID userId) {
            Instant now = Instant.now();
            claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .issuer(ISSUER)
                    .audience(AUDIENCE)
                    .claim("role", "authenticated")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(Duration.ofHours(1))));
        }

        public Builder issuer(String issuer) {
            claims.issuer(issuer);
            return this;
        }

        public Builder audience(String audience) {
            claims.audience(audience);
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            claims.expirationTime(Date.from(expiresAt));
            return this;
        }

        public Builder claim(String name, Object value) {
            claims.claim(name, value);
            return this;
        }

        /** Assina com uma chave que não está no JWKS, como um token forjado. */
        public Builder signedByUnknownKey() {
            signingKey = generateKey("chave-desconhecida");
            return this;
        }

        public String sign() {
            try {
                SignedJWT jwt = new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(KEY.getKeyID()).build(),
                        claims.build());
                jwt.sign(new ECDSASigner(signingKey));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static ECKey generateKey(String keyId) {
        try {
            return new ECKeyGenerator(Curve.P_256).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/auth/v1/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
