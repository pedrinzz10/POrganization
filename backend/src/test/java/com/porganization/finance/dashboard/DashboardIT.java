package com.porganization.finance.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.finance.support.FinanceFixture;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class DashboardIT extends FinanceFixture {

    @BeforeEach
    void hoje() {
        clock.setInstant(Instant.parse("2026-10-15T15:00:00Z"));
    }

    private String getJson(String url, String... params) throws Exception {
        var request = get(url).with(usuario(userId));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // F11 T1 (CA1)
    @Test
    void numerosBatemComOsEndpointsIndividuais() throws Exception {
        String corrente = conta("Corrente", "1000.00");
        String poupanca = conta("Poupança", "500.00");
        String alimentacao = categoria("Alimentação");
        transacao("INCOME", "5000.00", "2026-10-05", corrente, categoria("Salário"), true);
        transacao("EXPENSE", "300.00", "2026-10-06", corrente, alimentacao, true);
        transacao("EXPENSE", "200.00", "2026-10-07", corrente, categoria("Transporte"), true);
        transacao("EXPENSE", "150.00", "2026-10-20", corrente, alimentacao, false);
        mockMvc.perform(post("/api/finance/transfers").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fromAccountId":"%s","toAccountId":"%s","amount":"100.00","date":"2026-10-08"}
                        """.formatted(corrente, poupanca)))
                .andExpect(status().isCreated());
        String cartao = postJson("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":5,"dueDay":12,"paymentAccountId":"%s"}
                """.formatted(corrente));
        mockMvc.perform(post("/api/finance/cards/" + cartao + "/purchases").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"300.00\",\"date\":\"2026-10-10\",\"categoryId\":\"%s\",\"installments\":3}"
                                .formatted(alimentacao)))
                .andExpect(status().isCreated());
        postJson("/api/finance/budgets", "{\"categoryId\":\"%s\",\"amount\":\"500.00\"}".formatted(alimentacao));
        String meta = postJson("/api/finance/goals", "{\"name\":\"Reserva\",\"targetAmount\":\"6000.00\",\"targetDate\":\"2027-04-30\"}");
        postJson("/api/finance/goals/" + meta + "/contributions", "{\"amount\":\"1500.00\",\"date\":\"2026-10-01\"}");

        String dash = getJson("/api/finance/dashboard", "month", "2026-10");

        // Saldo total = soma dos saldos de /accounts
        List<String> saldos = JsonPath.read(getJson("/api/finance/accounts"), "$[*].balance");
        BigDecimal soma = saldos.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(new BigDecimal((String) JsonPath.read(dash, "$.totalBalance"))).isEqualByComparingTo(soma);
        assertThat((List<?>) JsonPath.read(dash, "$.accounts")).hasSize(2);

        // Renda x gasto = /summary. As 3 parcelas têm a data da compra: 300+200+150 + 300 do cartão
        String resumo = getJson("/api/finance/summary", "month", "2026-10");
        for (String campo : List.of("income", "expense", "net")) {
            assertThat((Object) JsonPath.read(dash, "$." + campo)).isEqualTo(JsonPath.read(resumo, "$." + campo));
        }
        assertThat((String) JsonPath.read(dash, "$.expense")).isEqualTo("950.00");

        // Gasto por categoria, do maior para o menor
        assertThat((List<String>) JsonPath.read(dash, "$.expenseByCategory[*].name")).containsExactly("Alimentação", "Transporte");
        assertThat((List<String>) JsonPath.read(dash, "$.expenseByCategory[*].total")).containsExactly("750.00", "200.00");

        // Faturas e limite = /cards/{id}/statements
        String faturas = getJson("/api/finance/cards/" + cartao + "/statements");
        assertThat((List<?>) JsonPath.read(dash, "$.cards[0].openStatements[*].total"))
                .isEqualTo(JsonPath.read(faturas, "$[?(@.status != 'PAID')].total"));
        String fatura = getJson("/api/finance/cards/" + cartao + "/statements", "month", "2026-11");
        assertThat((Object) JsonPath.read(dash, "$.cards[0].availableLimit")).isEqualTo(JsonPath.read(fatura, "$.availableLimit"));

        // Orçamentos em alerta = os de /budgets fora do OK
        String orcamentos = getJson("/api/finance/budgets", "month", "2026-10");
        assertThat((Object) JsonPath.read(dash, "$.budgetAlerts")).isEqualTo(JsonPath.read(orcamentos, "$[?(@.level != 'OK')]"));
        assertThat((String) JsonPath.read(dash, "$.budgetAlerts[0].level")).isEqualTo("ESTOURADO");

        // Metas = /goals
        assertThat((Object) JsonPath.read(dash, "$.goals")).isEqualTo(JsonPath.read(getJson("/api/finance/goals"), "$"));
        assertThat((String) JsonPath.read(dash, "$.goals[0].progress")).isEqualTo("25.00");
    }

    // F11 T2 (CA2)
    @Test
    void serieDeSeisMesesTrazMesesSemMovimentoComZero() throws Exception {
        String corrente = conta("Corrente", "0.00");
        transacao("EXPENSE", "80.00", "2026-10-03", corrente, categoria("Lazer"), true);

        mockMvc.perform(get("/api/finance/dashboard").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$.lastSixMonths", hasSize(6)))
                .andExpect(jsonPath("$.lastSixMonths[*].month").value(
                        contains("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10")))
                .andExpect(jsonPath("$.lastSixMonths[*].expense").value(
                        contains("0.00", "0.00", "0.00", "0.00", "0.00", "80.00")))
                .andExpect(jsonPath("$.lastSixMonths[*].income").value(
                        contains("0.00", "0.00", "0.00", "0.00", "0.00", "0.00")));
    }

    @Test
    void usuarioSemNadaRecebeDashboardVazio() throws Exception {
        mockMvc.perform(get("/api/finance/dashboard").param("month", "2026-10").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value("0.00"))
                .andExpect(jsonPath("$.cards", hasSize(0)))
                .andExpect(jsonPath("$.goals", hasSize(0)));
    }
}
