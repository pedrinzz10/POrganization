package com.porganization.today;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TodayControllerIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();

    private void criar(String json) throws Exception {
        mockMvc.perform(post("/api/commitments").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    // C10 T1 (CA1)
    @Test
    void usaODiaNoFusoDoUsuario() throws Exception {
        criar("{\"title\":\"Reunião\",\"date\":\"2026-10-01\",\"startTime\":\"15:00\"}");
        criar("{\"title\":\"Aniversário\",\"date\":\"2026-10-01\"}");
        criar("{\"title\":\"Amanhã\",\"date\":\"2026-10-02\",\"startTime\":\"09:00\"}");

        // 02:30 em UTC é 23:30 do dia 1º em São Paulo (fuso padrão, sem user_settings)
        clock.setInstant(Instant.parse("2026-10-02T02:30:00Z"));

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-01"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.commitments", hasSize(2)))
                .andExpect(jsonPath("$.commitments[*].title").value(contains("Aniversário", "Reunião")));
    }

    @Test
    void respeitaOFusoConfiguradoPeloUsuario() throws Exception {
        jdbc.update("insert into user_settings (user_id, timezone) values (?, 'Europe/Lisbon')", userId);
        criar("{\"title\":\"Em Lisboa\",\"date\":\"2026-10-02\",\"startTime\":\"10:00\"}");

        // mesmo instante: em Lisboa já são 03:30 do dia 2
        clock.setInstant(Instant.parse("2026-10-02T02:30:00Z"));

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.date").value("2026-10-02"))
                .andExpect(jsonPath("$.commitments[*].title").value(contains("Em Lisboa")));
    }

    @Test
    void incluiOcorrenciasDeCompromissosRecorrentes() throws Exception {
        criar("""
                {"title":"Remédio","date":"2026-09-01","startTime":"08:00","recurrenceRule":{"freq":"DAILY"}}
                """);
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.commitments[0].title").value("Remédio"))
                .andExpect(jsonPath("$.commitments[0].occurrenceDate").value("2026-10-01"))
                .andExpect(jsonPath("$.commitments[0].recurring").value(true));
    }

    @Test
    void semTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/today")).andExpect(status().isUnauthorized());
    }
}
