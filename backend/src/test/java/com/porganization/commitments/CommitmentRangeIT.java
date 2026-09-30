package com.porganization.commitments;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porganization.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CommitmentRangeIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();

    private void criar(UUID dono, String json) throws Exception {
        mockMvc.perform(post("/api/commitments").with(usuario(dono))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    @BeforeEach
    void cenario() throws Exception {
        criar(userId, "{\"title\":\"Reunião\",\"date\":\"2026-10-01\",\"startTime\":\"10:00\"}");
        criar(userId, "{\"title\":\"Feriado da cidade\",\"date\":\"2026-10-01\"}");
        criar(userId, "{\"title\":\"Academia\",\"date\":\"2026-10-02\",\"startTime\":\"08:00\"}");
        criar(userId, "{\"title\":\"Viagem\",\"date\":\"2026-10-10\",\"startTime\":\"06:00\"}");
        // de outro usuário, no mesmo intervalo
        criar(UUID.randomUUID(), "{\"title\":\"De outra pessoa\",\"date\":\"2026-10-01\",\"startTime\":\"09:00\"}");
    }

    // C03 T1 (CA1)
    @Test
    void devolveSoOIntervaloOrdenadoPorDataDiaTodoEHorario() throws Exception {
        mockMvc.perform(get("/api/commitments").param("from", "2026-10-01").param("to", "2026-10-02")
                        .with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].title").value(contains("Feriado da cidade", "Reunião", "Academia")))
                .andExpect(jsonPath("$[0].allDay").value(true));
    }

    // C03 T2 (CA2)
    @Test
    void toAntesDeFromResponde400() throws Exception {
        mockMvc.perform(get("/api/commitments").param("from", "2026-10-10").param("to", "2026-10-01")
                        .with(usuario(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"));
    }

    // C03 T2 (CA2)
    @Test
    void intervaloDeMaisDe400DiasResponde400() throws Exception {
        mockMvc.perform(get("/api/commitments").param("from", "2026-01-01").param("to", "2027-02-06")
                        .with(usuario(userId)))
                .andExpect(status().isBadRequest());
        // 400 dias ainda é aceito (cabe a visão de ano com folga)
        mockMvc.perform(get("/api/commitments").param("from", "2026-01-01").param("to", "2027-02-05")
                        .with(usuario(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void semParametrosResponde400() throws Exception {
        mockMvc.perform(get("/api/commitments").with(usuario(userId))).andExpect(status().isBadRequest());
    }
}
