package com.porganization.finance.goals;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.finance.support.FinanceFixture;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class GoalIT extends FinanceFixture {

    @BeforeEach
    void hoje() {
        clock.setInstant(Instant.parse("2026-10-15T15:00:00Z"));
    }

    private String aporte(String meta, String valor) throws Exception {
        String body = mockMvc.perform(post("/api/finance/goals/" + meta + "/contributions").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"%s\",\"date\":\"2026-10-01\"}".formatted(valor)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // F10 T1 (CA1)
    @Test
    void progressoEQuantoFalta() throws Exception {
        String meta = postJson("/api/finance/goals", """
                {"name":"Reserva de emergência","targetAmount":"6000.00","targetDate":"2027-04-30"}
                """);
        aporte(meta, "1000.00");
        aporte(meta, "500.00");

        mockMvc.perform(get("/api/finance/goals/" + meta).with(usuario(userId)))
                .andExpect(jsonPath("$.saved").value("1500.00"))
                .andExpect(jsonPath("$.progress").value("25.00"))
                .andExpect(jsonPath("$.remaining").value("4500.00"))
                // out → abr = 6 meses
                .andExpect(jsonPath("$.monthlyNeeded").value("750.00"))
                .andExpect(jsonPath("$.achieved").value(false));
        mockMvc.perform(get("/api/finance/goals").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].progress").value("25.00"));
    }

    @Test
    void metaBatidaNaoPedeMaisAporte() throws Exception {
        String meta = postJson("/api/finance/goals", "{\"name\":\"Viagem\",\"targetAmount\":\"1000.00\",\"targetDate\":\"2027-01-01\"}");
        aporte(meta, "1200.00");

        mockMvc.perform(get("/api/finance/goals/" + meta).with(usuario(userId)))
                .andExpect(jsonPath("$.remaining").value("0.00"))
                .andExpect(jsonPath("$.progress").value("120.00"))
                .andExpect(jsonPath("$.monthlyNeeded").value("0.00"))
                .andExpect(jsonPath("$.achieved").value(true));
    }

    @Test
    void excluirAporteRecalculaEOutroUsuarioNaoVe() throws Exception {
        String meta = postJson("/api/finance/goals", "{\"name\":\"Notebook\",\"targetAmount\":\"5000.00\"}");
        String primeiro = aporte(meta, "1000.00");
        aporte(meta, "250.00");

        mockMvc.perform(delete("/api/finance/goals/" + meta + "/contributions/" + primeiro).with(usuario(userId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/finance/goals/" + meta).with(usuario(userId)))
                .andExpect(jsonPath("$.saved").value("250.00"))
                .andExpect(jsonPath("$.monthlyNeeded").doesNotExist());
        mockMvc.perform(get("/api/finance/goals/" + meta + "/contributions").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/finance/goals/" + meta).with(usuario(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void contaDeOutroUsuarioResponde404() throws Exception {
        mockMvc.perform(post("/api/finance/goals").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"targetAmount\":\"10.00\",\"accountId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
