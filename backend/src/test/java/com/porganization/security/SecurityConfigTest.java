package com.porganization.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porganization.support.IntegrationTest;
import com.porganization.support.TestJwks;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/** Tokens de verdade, assinados pelo TestJwks e validados pelo JwtDecoder da aplicação. */
class SecurityConfigTest extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();

    private ResultActions getMe(String token) throws Exception {
        return mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // B04 T1 (CA1)
    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    // B04 T1 (CA1)
    @Test
    void tokenExpiradoResponde401() throws Exception {
        String expirado = TestJwks.builder(userId)
                .expiresAt(Instant.now().minus(Duration.ofMinutes(5)))
                .sign();
        getMe(expirado).andExpect(status().isUnauthorized());
    }

    // B04 T1 (CA1)
    @Test
    void tokenAssinadoPorChaveDesconhecidaResponde401() throws Exception {
        getMe(TestJwks.builder(userId).signedByUnknownKey().sign()).andExpect(status().isUnauthorized());
    }

    // B04 T1 (CA1)
    @Test
    void tokenMalformadoResponde401() throws Exception {
        getMe("nao-e-um-jwt").andExpect(status().isUnauthorized());
    }

    // B04 T4 (CA4)
    @Test
    void issuerDeOutroProjetoResponde401() throws Exception {
        String token = TestJwks.builder(userId).issuer("https://outro.supabase.co/auth/v1").sign();
        getMe(token).andExpect(status().isUnauthorized());
    }

    // B04 T4 (CA4)
    @Test
    void audienceDiferenteDeAuthenticatedResponde401() throws Exception {
        getMe(TestJwks.builder(userId).audience("anon").sign()).andExpect(status().isUnauthorized());
    }

    // B04 T4 (CA4)
    @Test
    void tokenValidoResponde200() throws Exception {
        getMe(TestJwks.token(userId, "pedro@teste.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }
}
