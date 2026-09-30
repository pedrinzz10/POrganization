package com.porganization.support;

import com.porganization.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes de integração: contexto completo, MockMvc e Postgres do Testcontainers
 * com todas as migrações aplicadas. Todas as subclasses compartilham o mesmo contexto
 * (e o mesmo container), então só o primeiro teste paga o custo de subir.
 * O JWT é validado contra o JWKS local do TestJwks, no lugar do Supabase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String FRONTEND_ORIGIN = "http://localhost:4200";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    // Cada teste começa com o relógio real, mesmo que o anterior tenha "viajado no tempo"
    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Requisição autenticada como o usuário informado (JWT com sub = userId). */
    protected static JwtRequestPostProcessor usuario(UUID userId) {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.subject(userId.toString()));
    }

    @DynamicPropertySource
    static void supabaseAuth(DynamicPropertyRegistry registry) {
        registry.add("SUPABASE_JWKS_URI", TestJwks::jwksUri);
        registry.add("SUPABASE_ISSUER", () -> TestJwks.ISSUER);
        registry.add("FRONTEND_ORIGIN", () -> FRONTEND_ORIGIN);
        // Jobs agendados não rodam nos testes; os testes chamam o job direto quando precisam
        registry.add("app.jobs.enabled", () -> "false");
    }
}
