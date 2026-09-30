package com.porganization.finance.budgets;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porganization.finance.support.FinanceFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class BudgetIT extends FinanceFixture {

    private String conta;
    private String alimentacao;

    @BeforeEach
    void cenario() throws Exception {
        conta = conta("Corrente", "0.00");
        alimentacao = categoria("Alimentação");
    }

    private String orcamento(String categoria, String mes, String valor) throws Exception {
        return postJson("/api/finance/budgets", """
                {"categoryId":"%s","month":%s,"amount":"%s"}
                """.formatted(categoria, mes == null ? "null" : "\"" + mes + "\"", valor));
    }

    // F09 T2 (CA2)
    @Test
    void compraNoCartaoContaNoMesDaCompraNaoNoDaFatura() throws Exception {
        String cartao = postJson("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":5,"dueDay":12,"paymentAccountId":"%s"}
                """.formatted(conta));
        orcamento(alimentacao, null, "500.00");
        // 20/10 é depois do fechamento (5): cai na fatura de novembro
        mockMvc.perform(post("/api/finance/cards/" + cartao + "/purchases").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"100.00\",\"date\":\"2026-10-20\",\"categoryId\":\"%s\"}".formatted(alimentacao)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/finance/budgets").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$[0].spent").value("100.00"))
                .andExpect(jsonPath("$[0].percent").value("20.00"))
                .andExpect(jsonPath("$[0].level").value("OK"));
        mockMvc.perform(get("/api/finance/budgets").param("month", "2026-11").with(usuario(userId)))
                .andExpect(jsonPath("$[0].spent").value("0.00"));
    }

    @Test
    void gastosPagosENaoPagosContamSoNoMesDeles() throws Exception {
        orcamento(alimentacao, null, "500.00");
        transacao("EXPENSE", "300.00", "2026-10-03", conta, alimentacao, true);
        transacao("EXPENSE", "100.00", "2026-10-15", conta, alimentacao, false);
        transacao("EXPENSE", "999.00", "2026-11-01", conta, alimentacao, true);

        mockMvc.perform(get("/api/finance/budgets").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].categoryName").value("Alimentação"))
                .andExpect(jsonPath("$[0].amount").value("500.00"))
                .andExpect(jsonPath("$[0].spent").value("400.00"))
                .andExpect(jsonPath("$[0].remaining").value("100.00"))
                .andExpect(jsonPath("$[0].level").value("ATENCAO"));
    }

    @Test
    void orcamentoDoMesValeNoLugarDoRecorrente() throws Exception {
        orcamento(alimentacao, null, "500.00");
        orcamento(alimentacao, "2026-12", "900.00");

        mockMvc.perform(get("/api/finance/budgets").param("month", "2026-12").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value("900.00"))
                .andExpect(jsonPath("$[0].month").value("2026-12"));
        mockMvc.perform(get("/api/finance/budgets").param("month", "2026-11").with(usuario(userId)))
                .andExpect(jsonPath("$[0].amount").value("500.00"))
                .andExpect(jsonPath("$[0].month").doesNotExist());
    }

    @Test
    void pagamentoDeFaturaNaoContaDeNovo() throws Exception {
        String cartao = postJson("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":5,"dueDay":12,"paymentAccountId":"%s"}
                """.formatted(conta));
        orcamento(alimentacao, null, "500.00");
        mockMvc.perform(post("/api/finance/cards/" + cartao + "/purchases").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"450.00\",\"date\":\"2026-10-01\",\"categoryId\":\"%s\"}".formatted(alimentacao)));
        String fatura = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements")
                .param("month", "2026-10").with(usuario(userId))).andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/finance/cards/statements/" + fatura + "/pay").with(usuario(userId)));

        mockMvc.perform(get("/api/finance/budgets").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$[0].spent").value("450.00"));
    }

    @Test
    void duplicadoResponde409CategoriaDeRendaResponde400() throws Exception {
        orcamento(alimentacao, "2026-10", "500.00");
        mockMvc.perform(post("/api/finance/budgets").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"%s\",\"month\":\"2026-10\",\"amount\":\"100.00\"}".formatted(alimentacao)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/finance/budgets").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"%s\",\"amount\":\"100.00\"}".formatted(categoria("Salário"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editarValorEExcluir() throws Exception {
        String id = orcamento(alimentacao, "2026-10", "500.00");

        mockMvc.perform(put("/api/finance/budgets/" + id).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"650.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("650.00"));
        mockMvc.perform(delete("/api/finance/budgets/" + id).with(usuario(userId))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/finance/budgets").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
