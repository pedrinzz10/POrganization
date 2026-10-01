package com.porganization.finance.recurring;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.finance.support.FinanceFixture;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class RecurringGeneratorIT extends FinanceFixture {

    @Autowired
    private RecurringJob job;

    private String conta;

    @BeforeEach
    void conta() throws Exception {
        conta = conta("Corrente", "0.00");
    }

    private String aluguel(String valor, String inicio, String fim) throws Exception {
        return postJson("/api/finance/recurring", """
                {"type":"EXPENSE","amount":"%s","description":"Aluguel","accountId":"%s","categoryId":"%s",
                 "dayOfMonth":10,"startMonth":"%s","endMonth":%s}
                """.formatted(valor, conta, categoria("Moradia"), inicio, fim == null ? "null" : "\"" + fim + "\""));
    }

    private void gerar(String mes, int criadas) throws Exception {
        mockMvc.perform(post("/api/finance/recurring/generate").param("month", mes).with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(criadas));
    }

    // F08 T1 (CA1)
    @Test
    void gerarOMesmoMesDuasVezesNaoDuplica() throws Exception {
        String modelo = aluguel("1500.00", "2026-01", null);

        gerar("2026-10", 1);
        gerar("2026-10", 0);

        mockMvc.perform(get("/api/finance/transactions").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value("2026-10-10"))
                .andExpect(jsonPath("$[0].amount").value("1500.00"))
                .andExpect(jsonPath("$[0].paid").value(false))
                .andExpect(jsonPath("$[0].recurringId").value(modelo));
    }

    @Test
    void consultarOMesGeraOsFixosDele() throws Exception {
        aluguel("1500.00", "2026-01", null);

        mockMvc.perform(get("/api/finance/summary").param("month", "2026-11").with(usuario(userId)))
                .andExpect(jsonPath("$.expense").value("1500.00"));
        mockMvc.perform(get("/api/finance/transactions").param("month", "2026-11").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // F08 T3 (CA3)
    @Test
    void recorrenteNoCartaoCaiNaFaturaCerta() throws Exception {
        String cartao = postJson("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":5,"dueDay":12,"paymentAccountId":"%s"}
                """.formatted(conta));
        postJson("/api/finance/recurring", """
                {"type":"EXPENSE","amount":"39.90","description":"Streaming","cardId":"%s","categoryId":"%s",
                 "dayOfMonth":7,"startMonth":"2026-10"}
                """.formatted(cartao, categoria("Lazer")));

        gerar("2026-10", 1);

        // Dia 7 é depois do fechamento (5): fatura que fecha em novembro e vence em 12/11
        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].referenceMonth").value("2026-11"))
                .andExpect(jsonPath("$[0].total").value("39.90"));
        // F24 T1: o item da fatura diz que veio de uma assinatura
        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").param("month", "2026-11").with(usuario(userId)))
                .andExpect(jsonPath("$.items[0].description").value("Streaming"))
                .andExpect(jsonPath("$.items[0].recurringId").isNotEmpty());
    }

    /** F22: o mês já gerado e ainda em aberto acompanha a edição (o pago não muda, ver RecurringEditIT). */
    @Test
    void editarOModeloAtualizaOMesEmAberto() throws Exception {
        String modelo = aluguel("1500.00", "2026-01", null);
        gerar("2026-10", 1);

        mockMvc.perform(put("/api/finance/recurring/" + modelo).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"EXPENSE","amount":"1600.00","description":"Aluguel","accountId":"%s","categoryId":"%s",
                                 "dayOfMonth":10,"startMonth":"2026-01"}
                                """.formatted(conta, categoria("Moradia"))))
                .andExpect(status().isOk());
        gerar("2026-11", 1);

        mockMvc.perform(get("/api/finance/transactions").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$[*].amount").value(contains("1600.00")));
        mockMvc.perform(get("/api/finance/transactions").param("month", "2026-11").with(usuario(userId)))
                .andExpect(jsonPath("$[*].amount").value(contains("1600.00")));
    }

    @Test
    void lancamentoGeradoExcluidoNaoVolta() throws Exception {
        aluguel("1500.00", "2026-01", null);
        String body = mockMvc.perform(get("/api/finance/transactions").param("month", "2026-10").with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        String gerado = JsonPath.read(body, "$[0].id");

        mockMvc.perform(delete("/api/finance/transactions/" + gerado).with(usuario(userId))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/finance/transactions").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void foraDoPeriodoDoModeloNaoGera() throws Exception {
        aluguel("1500.00", "2026-03", "2026-05");
        gerar("2026-02", 0);
        gerar("2026-05", 1);
        gerar("2026-06", 0);
    }

    /** F22: o lançamento em aberto sai junto com o modelo (o confirmado fica, ver RecurringEditIT). */
    @Test
    void excluirOModeloTiraOsLancamentosEmAberto() throws Exception {
        String modelo = aluguel("1500.00", "2026-01", null);
        gerar("2026-10", 1);

        mockMvc.perform(delete("/api/finance/recurring/" + modelo).with(usuario(userId))).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/finance/transactions").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/finance/recurring").with(usuario(userId))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void jobDiarioGeraOMesCorrente() throws Exception {
        aluguel("1500.00", "2026-01", null);
        clock.setInstant(Instant.parse("2026-12-01T09:00:00Z"));

        job.run();

        mockMvc.perform(post("/api/finance/recurring/generate").param("month", "2026-12").with(usuario(userId)))
                .andExpect(jsonPath("$.created").value(0));
    }

    @Test
    void contaECartaoJuntosOuRendaNoCartaoResponde400() throws Exception {
        String cartao = postJson("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":5,"dueDay":12,"paymentAccountId":"%s"}
                """.formatted(conta));
        mockMvc.perform(post("/api/finance/recurring").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"EXPENSE","amount":"10.00","accountId":"%s","cardId":"%s","categoryId":"%s","dayOfMonth":1,"startMonth":"2026-10"}
                        """.formatted(conta, cartao, categoria("Lazer"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/finance/recurring").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"INCOME","amount":"10.00","cardId":"%s","categoryId":"%s","dayOfMonth":1,"startMonth":"2026-10"}
                        """.formatted(cartao, categoria("Salário"))))
                .andExpect(status().isBadRequest());
    }
}
