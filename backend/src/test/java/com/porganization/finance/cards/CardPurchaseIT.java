package com.porganization.finance.cards;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.finance.support.FinanceFixture;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CardPurchaseIT extends FinanceFixture {

    private String cartao;

    @BeforeEach
    void cartao() throws Exception {
        cartao = postJson("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":5,"dueDay":12,"paymentAccountId":"%s"}
                """.formatted(conta("Corrente", "0.00")));
    }

    private String comprarParcelado(String valor, String data, int parcelas) throws Exception {
        String body = mockMvc.perform(post("/api/finance/cards/" + cartao + "/purchases").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"amount":"%s","date":"%s","description":"Loja","categoryId":"%s","installments":%d}
                                """.formatted(valor, data, categoria("Lazer"), parcelas)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.purchaseId");
    }

    // F06 T2 (CA2)
    @Test
    void cadaParcelaCaiNumaFaturaConsecutiva() throws Exception {
        comprarParcelado("100.00", "2026-10-01", 3);

        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].referenceMonth").value(contains("2026-10", "2026-11", "2026-12")))
                .andExpect(jsonPath("$[*].total").value(contains("33.34", "33.33", "33.33")));

        mockMvc.perform(get("/api/finance/transactions").with(usuario(userId)))
                .andExpect(jsonPath("$[*].description").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "Loja (1/3)", "Loja (2/3)", "Loja (3/3)")))
                .andExpect(jsonPath("$[?(@.description == 'Loja (2/3)')].installmentNumber").value(contains(2)))
                .andExpect(jsonPath("$[?(@.description == 'Loja (2/3)')].installmentCount").value(contains(3)));
    }

    // F06 T3 (CA3)
    @Test
    void excluirACompraRemoveTodasAsParcelas() throws Exception {
        String compra = comprarParcelado("100.00", "2026-10-01", 3);

        mockMvc.perform(delete("/api/finance/cards/purchases/" + compra).with(usuario(userId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/finance/cards/" + cartao + "/statements").with(usuario(userId)))
                .andExpect(jsonPath("$[*].total").value(contains("0.00", "0.00", "0.00")));
        mockMvc.perform(get("/api/finance/transactions").with(usuario(userId))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void compraAVistaNaoGanhaSufixoDeParcela() throws Exception {
        comprarParcelado("59.90", "2026-10-01", 1);
        mockMvc.perform(get("/api/finance/transactions").with(usuario(userId)))
                .andExpect(jsonPath("$[0].description").value("Loja"))
                .andExpect(jsonPath("$[0].amount").value("59.90"));
    }

    @Test
    void valorPequenoDemaisParaAsParcelasResponde400() throws Exception {
        mockMvc.perform(post("/api/finance/cards/" + cartao + "/purchases").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"0.02\",\"date\":\"2026-10-01\",\"categoryId\":\"%s\",\"installments\":3}".formatted(categoria("Lazer"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("installments"));
    }

    @Test
    void compraDeOutroUsuarioResponde404() throws Exception {
        String compra = comprarParcelado("100.00", "2026-10-01", 2);
        mockMvc.perform(delete("/api/finance/cards/purchases/" + compra).with(usuario(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }
}
