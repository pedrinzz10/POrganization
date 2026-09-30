package com.porganization.finance.dashboard;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.finance.support.FinanceFixture;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ForecastIT extends FinanceFixture {

    private String bradesco;

    /** Hoje é 15/10/2026. */
    @BeforeEach
    void cenario() throws Exception {
        clock.setInstant(Instant.parse("2026-10-15T15:00:00Z"));
        bradesco = conta("Bradesco", "1100.00");
        conta("Nubank", "300.00");
    }

    private void agendado(String descricao, String tipo, String valor, String categoria, int dia) throws Exception {
        postJson("/api/finance/recurring", """
                {"type":"%s","amount":"%s","description":"%s","accountId":"%s","categoryId":"%s","startMonth":"2026-10","dayOfMonth":%d}
                """.formatted(tipo, valor, descricao, bradesco, categoria(categoria), dia));
    }

    private String ocorrencia(String descricao) throws Exception {
        String body = mockMvc.perform(get("/api/finance/scheduled").param("month", "2026-10").with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.description == '" + descricao + "')].id");
        return ids.getFirst();
    }

    // F21 T1 (CA1)
    @Test
    void previstoSomaOQueFaltaReceberEDescontaOQueFaltaPagar() throws Exception {
        agendado("Salário", "INCOME", "3000.00", "Salário", 7);     // atrasado
        agendado("Aluguel", "EXPENSE", "1200.00", "Moradia", 25);   // previsto
        agendado("Internet", "EXPENSE", "100.00", "Moradia", 10);   // já paga
        mockMvc.perform(post("/api/finance/scheduled/" + ocorrencia("Internet") + "/confirm").with(usuario(userId)))
                .andExpect(status().isOk());

        // Bradesco 1100 − 100 = 1000; Nubank 300 → total 1300; previsto 1300 + 3000 − 1200 = 3100
        mockMvc.perform(get("/api/finance/dashboard").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$.totalBalance").value("1300.00"))
                .andExpect(jsonPath("$.receivable").value("3000.00"))
                .andExpect(jsonPath("$.payable").value("1200.00"))
                .andExpect(jsonPath("$.forecast").value("3100.00"));
    }

    // F21 T2 (CA2)
    @Test
    void remarcadoParaOMesSeguinteSaiDoPrevistoDesteMes() throws Exception {
        agendado("Salário", "INCOME", "3000.00", "Salário", 30);
        agendado("Aluguel", "EXPENSE", "1200.00", "Moradia", 25);
        mockMvc.perform(post("/api/finance/scheduled/" + ocorrencia("Salário") + "/reschedule").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-11-03\"}"))
                .andExpect(status().isOk());

        // 1100 + 300 − 1200 = 200
        mockMvc.perform(get("/api/finance/dashboard").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$.receivable").value("0.00"))
                .andExpect(jsonPath("$.forecast").value("200.00"));
        // Em novembro ele volta a contar
        mockMvc.perform(get("/api/finance/dashboard").param("month", "2026-11").with(usuario(userId)))
                .andExpect(jsonPath("$.receivable").value("6000.00"));
    }

    @Test
    void canceladoNaoContaETelaHojeTrazOsMesmosNumeros() throws Exception {
        agendado("Salário", "INCOME", "3000.00", "Salário", 20);
        mockMvc.perform(post("/api/finance/scheduled/" + ocorrencia("Salário") + "/skip").with(usuario(userId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/today").with(usuario(userId)))
                .andExpect(jsonPath("$.finance.balances.totalBalance").value("1400.00"))
                .andExpect(jsonPath("$.finance.balances.forecast").value("1400.00"))
                .andExpect(jsonPath("$.finance.balances.accounts[*].name").value(containsInAnyOrder("Bradesco", "Nubank")));
    }
}
