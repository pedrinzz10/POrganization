package com.porganization.integrations.google;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.porganization.support.IntegrationTest;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base dos testes da integração com o Google: um WireMock faz o papel do Google (OAuth e Calendar
 * API) e as credenciais são de mentira. As subclasses compartilham o contexto e o WireMock.
 */
abstract class GoogleIT extends IntegrationTest {

    protected static final WireMockServer google = new WireMockServer(wireMockConfig().dynamicPort());

    // Compartilhado pelas subclasses (mesmo contexto do Spring); para junto com o JVM dos testes
    static {
        google.start();
    }

    @DynamicPropertySource
    static void googleDeMentira(DynamicPropertyRegistry registry) {
        registry.add("GOOGLE_CLIENT_ID", () -> "client-id-de-teste");
        registry.add("GOOGLE_CLIENT_SECRET", () -> "client-secret-de-teste");
        registry.add("GOOGLE_REDIRECT_URI", () -> "http://localhost:8080/api/integrations/google/callback");
        registry.add("GOOGLE_TOKEN_KEY", () -> Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        registry.add("APP_URL", () -> "https://app.porganization.test");
        registry.add("porganization.google.auth-uri", () -> google.baseUrl() + "/o/oauth2/v2/auth");
        registry.add("porganization.google.token-uri", () -> google.baseUrl() + "/token");
        registry.add("porganization.google.revoke-uri", () -> google.baseUrl() + "/revoke");
        registry.add("porganization.google.calendar-api", () -> google.baseUrl() + "/calendar/v3");
    }

    @BeforeEach
    void limparGoogle() {
        google.resetAll();
    }
}
