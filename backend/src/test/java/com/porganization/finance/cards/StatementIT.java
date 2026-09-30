package com.porganization.finance.cards;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.finance.support.FinanceFixture;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class StatementIT extends FinanceFixture {

    private String conta;
    private String cartao;

    @BeforeEach
    void cartao() throws Exception {
        clock.setInstant(Instant.parse("2026-10-01T15:00:00Z"));
        conta = conta("Corrente", "1000.00");
        cartao = postJson("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":5,"dueDay":12,"paymentAccountId":"%s"}
                """.formatted(conta));
    }

    private void comprar(String valor, String data, int parcelas) throws Exception {
        mockMvc.perform(post("/api/finance/cards/" + cartao + "/purchases").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"amount":"%s","date":"%s","description":"Loja","categoryId":"%s","installments":%d}
                                """.formatted(valor, data, categoria("Lazer"), parcelas)))
                .andExpect(status().isCreated());
    }

    private String faturaId(String mes) throws Exception {
        String body = mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").param("month", mes).with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // F07 T1 (CA1)
    @Test
    void limiteDisponivelDescontaParcelasDeFaturasNaoPagas() throws Exception {
        comprar("100.00", "2026-10-01", 3);

        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").param("month", "2026-10").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("33.34"))
                .andExpect(jsonPath("$.creditLimit").value("3000.00"))
                .andExpect(jsonPath("$.availableLimit").value("2900.00"))
                .andExpect(jsonPath("$.items[0].description").value("Loja (1/3)"));

        mockMvc.perform(post("/api/finance/cards/statements/" + faturaId("2026-10") + "/pay").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableLimit").value("2933.34"));
    }

    // F07 T2 (CA2)
    @Test
    void pagarCriaGastoNaContaEMarcaPagaPagarDeNovoResponde409() throws Exception {
        comprar("450.00", "2026-10-01", 1);
        String fatura = faturaId("2026-10");

        mockMvc.perform(post("/api/finance/cards/statements/" + fatura + "/pay").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paymentTransactionId").isNotEmpty());
        org.assertj.core.api.Assertions.assertThat(saldo(conta)).isEqualTo("550.00");

        mockMvc.perform(post("/api/finance/cards/statements/" + fatura + "/pay").with(usuario(userId)))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(saldo(conta)).isEqualTo("550.00");
    }

    @Test
    void pagamentoNaoContaDuasVezesNoResumoDoMes() throws Exception {
        comprar("450.00", "2026-10-01", 1);
        mockMvc.perform(post("/api/finance/cards/statements/" + faturaId("2026-10") + "/pay").with(usuario(userId)));

        mockMvc.perform(get("/api/finance/summary").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$.expense").value("450.00"));
    }

    @Test
    void excluirOPagamentoReabreAFatura() throws Exception {
        comprar("450.00", "2026-10-01", 1);
        String body = mockMvc.perform(post("/api/finance/cards/statements/" + faturaId("2026-10") + "/pay").with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        String pagamento = JsonPath.read(body, "$.paymentTransactionId");

        mockMvc.perform(delete("/api/finance/transactions/" + pagamento).with(usuario(userId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.availableLimit").value("2550.00"));
        org.assertj.core.api.Assertions.assertThat(saldo(conta)).isEqualTo("1000.00");
    }

    // F07 T3 (CA3)
    @Test
    void depoisDoFechamentoAFaturaVemFechadaENovasComprasVaoParaAProxima() throws Exception {
        comprar("80.00", "2026-10-01", 1);
        clock.setInstant(Instant.parse("2026-10-06T15:00:00Z"));

        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").with(usuario(userId)))
                .andExpect(jsonPath("$[0].status").value("CLOSED"));

        comprar("20.00", "2026-10-06", 1);
        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").param("month", "2026-11").with(usuario(userId)))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.total").value("20.00"));
    }

    @Test
    void mesSemComprasVoltaFaturaVaziaComAsDatas() throws Exception {
        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").param("month", "2026-12").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.closingDate").value("2026-12-05"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-12"))
                .andExpect(jsonPath("$.total").value("0.00"))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void compraQueCairiaNumaFaturaPagaResponde409() throws Exception {
        comprar("50.00", "2026-10-01", 1);
        mockMvc.perform(post("/api/finance/cards/statements/" + faturaId("2026-10") + "/pay").with(usuario(userId)));

        mockMvc.perform(post("/api/finance/cards/" + cartao + "/purchases").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"10.00\",\"date\":\"2026-10-02\",\"categoryId\":\"%s\"}".formatted(categoria("Lazer"))))
                .andExpect(status().isConflict());
    }

    @Test
    void faturaDeOutroUsuarioResponde404() throws Exception {
        comprar("50.00", "2026-10-01", 1);
        mockMvc.perform(post("/api/finance/cards/statements/" + faturaId("2026-10") + "/pay")
                        .with(usuario(java.util.UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
