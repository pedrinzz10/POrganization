package com.porganization.finance.cards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porganization.finance.support.FinanceFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CreditCardIT extends FinanceFixture {

    private String cartao(String contaPagamento, int fecha, int vence) throws Exception {
        return postJson("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":%d,"dueDay":%d,"paymentAccountId":"%s"}
                """.formatted(fecha, vence, contaPagamento));
    }

    private String comprar(String cartao, String valor, String data) throws Exception {
        return postJson("/api/finance/cards/" + cartao + "/purchases", """
                {"amount":"%s","date":"%s","description":"Loja","categoryId":"%s"}
                """.formatted(valor, data, categoria("Lazer")));
    }

    // F05 T3 (CA3)
    @Test
    void compraNoCartaoNaoMexeNoSaldoDaContaEEntraNaFatura() throws Exception {
        String conta = conta("Corrente", "1000.00");
        String cartao = cartao(conta, 5, 12);

        comprar(cartao, "150.00", "2026-10-01");

        assertThat(saldo(conta)).isEqualTo("1000.00");
        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].referenceMonth").value("2026-10"))
                .andExpect(jsonPath("$[0].dueDate").value("2026-10-12"))
                .andExpect(jsonPath("$[0].total").value("150.00"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    // F05 (CA1) de ponta a ponta: compra no dia do fechamento vai para a fatura seguinte
    @Test
    void compraNoFechamentoCaiNaFaturaSeguinte() throws Exception {
        String cartao = cartao(conta("Corrente", "0.00"), 5, 12);
        comprar(cartao, "10.00", "2026-10-04");
        comprar(cartao, "20.00", "2026-10-05");
        comprar(cartao, "5.00", "2026-10-06");

        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].referenceMonth").value("2026-10"))
                .andExpect(jsonPath("$[0].total").value("10.00"))
                .andExpect(jsonPath("$[1].referenceMonth").value("2026-11"))
                .andExpect(jsonPath("$[1].total").value("25.00"));
    }

    @Test
    void compraAparecenaListaDeTransacoesComoGastoSemConta() throws Exception {
        String cartao = cartao(conta("Corrente", "0.00"), 5, 12);
        comprar(cartao, "150.00", "2026-10-01");

        mockMvc.perform(get("/api/finance/transactions").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$[0].accountId").doesNotExist())
                .andExpect(jsonPath("$[0].cardStatementId").isNotEmpty());
    }

    @Test
    void validaDiasEContaDePagamento() throws Exception {
        String conta = conta("Corrente", "0.00");
        mockMvc.perform(post("/api/finance/cards").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"creditLimit\":\"100.00\",\"closingDay\":32,\"dueDay\":10,\"paymentAccountId\":\"%s\"}".formatted(conta)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/finance/cards").with(usuario(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"creditLimit\":\"100.00\",\"closingDay\":5,\"dueDay\":10,\"paymentAccountId\":\"%s\"}".formatted(conta)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cartaoDeOutroUsuarioResponde404() throws Exception {
        String cartao = cartao(conta("Corrente", "0.00"), 5, 12);
        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").with(usuario(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
