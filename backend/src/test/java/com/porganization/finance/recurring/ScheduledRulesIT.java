package com.porganization.finance.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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

class ScheduledRulesIT extends FinanceFixture {

    private String conta;

    @BeforeEach
    void conta() throws Exception {
        // 1º de setembro de 2026, 12:00 em São Paulo
        clock.setInstant(Instant.parse("2026-09-01T15:00:00Z"));
        conta = conta("Bradesco", "1000.00");
    }

    private String agendado(String regra) throws Exception {
        return postJson("/api/finance/recurring", """
                {"type":"INCOME","amount":"3000.00","description":"Salário","accountId":"%s","categoryId":"%s",
                 "startMonth":"2026-09",%s}
                """.formatted(conta, categoria("Salário"), regra));
    }

    private List<String> datasDoMes(String mes) throws Exception {
        String body = mockMvc.perform(get("/api/finance/transactions").param("month", mes).with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].date");
    }

    // F18 T1 (CA1)
    @Test
    void quintoDiaUtilEDiaVinteAntecipadoCaemNasDatasCertas() throws Exception {
        agendado("\"ruleType\":\"BUSINESS_DAY\",\"businessDay\":5");

        assertThat(datasDoMes("2026-10")).containsExactly("2026-10-07");
        assertThat(datasDoMes("2026-11")).containsExactly("2026-11-09");

        // Aluguel recebido no dia 20, antecipado quando cai no fim de semana (20/09/2026 é domingo)
        postJson("/api/finance/recurring", """
                {"type":"INCOME","amount":"1200.00","description":"Aluguel","accountId":"%s","categoryId":"%s",
                 "startMonth":"2026-09","ruleType":"DAY_OF_MONTH","dayOfMonth":20,"adjustment":"ANTICIPATE"}
                """.formatted(conta, categoria("Salário")));
        // Setembro: 5º dia útil é 08/09 (07/09 é feriado) e o aluguel vai para sexta 18/09
        assertThat(datasDoMes("2026-09")).containsExactlyInAnyOrder("2026-09-18", "2026-09-08");
    }

    // F18 T3 (CA3)
    @Test
    void ocorrenciaNaoConfirmadaNaoEntraNoSaldo() throws Exception {
        agendado("\"ruleType\":\"BUSINESS_DAY\",\"businessDay\":1");
        // 1º dia útil de setembro/2026 é hoje (terça, 01/09)
        assertThat(datasDoMes("2026-09")).containsExactly("2026-09-01");

        assertThat(saldo(conta)).isEqualTo("1000.00");
    }

    @Test
    void respostaTrazARegraEAProximaData() throws Exception {
        String id = agendado("\"ruleType\":\"BUSINESS_DAY\",\"businessDay\":5");

        mockMvc.perform(get("/api/finance/recurring").with(usuario(userId)))
                .andExpect(jsonPath("$[?(@.id == '%s')].ruleType".formatted(id)).value(contains("BUSINESS_DAY")))
                .andExpect(jsonPath("$[?(@.id == '%s')].businessDay".formatted(id)).value(contains(5)))
                .andExpect(jsonPath("$[?(@.id == '%s')].nextDate".formatted(id)).value(contains("2026-09-08")));
    }

    @Test
    void semRuleTypeContinuaComoDiaDoMes() throws Exception {
        postJson("/api/finance/recurring", """
                {"type":"EXPENSE","amount":"99.90","description":"Internet","accountId":"%s","categoryId":"%s",
                 "dayOfMonth":20,"startMonth":"2026-09"}
                """.formatted(conta, categoria("Moradia")));

        // Sem ajuste (KEEP): fica no domingo 20/09
        assertThat(datasDoMes("2026-09")).containsExactly("2026-09-20");
    }

    @Test
    void regraSemOCampoDelaResponde400() throws Exception {
        mockMvc.perform(post("/api/finance/recurring").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"INCOME","amount":"10.00","accountId":"%s","categoryId":"%s","startMonth":"2026-09","ruleType":"BUSINESS_DAY"}
                        """.formatted(conta, categoria("Salário"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("businessDay"));
        mockMvc.perform(post("/api/finance/recurring").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"INCOME","amount":"10.00","accountId":"%s","categoryId":"%s","startMonth":"2026-09","ruleType":"BUSINESS_DAY","businessDay":16}
                        """.formatted(conta, categoria("Salário"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previaMostraAsProximasTresDatas() throws Exception {
        mockMvc.perform(post("/api/finance/recurring/preview").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"BUSINESS_DAY\",\"businessDay\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDates").value(contains("2026-09-08", "2026-10-07", "2026-11-09")));
        mockMvc.perform(post("/api/finance/recurring/preview").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"LAST_BUSINESS_DAY\"}"))
                .andExpect(jsonPath("$.nextDates").value(contains("2026-09-30", "2026-10-30", "2026-11-30")));
    }
}
