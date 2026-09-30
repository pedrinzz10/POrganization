package com.porganization.finance.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.finance.support.FinanceFixture;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TransferIT extends FinanceFixture {

    private String transferir(String de, String para, String valor, String data) throws Exception {
        String body = mockMvc.perform(post("/api/finance/transfers").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromAccountId":"%s","toAccountId":"%s","amount":"%s","date":"%s","description":"Reserva"}
                                """.formatted(de, para, valor, data)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.groupId");
    }

    // F04 T1 (CA1)
    @Test
    void transferirMoveOSaldoSemMudarOTotal() throws Exception {
        String a = conta("Corrente", "500.00");
        String b = conta("Poupança", "0.00");

        transferir(a, b, "200.00", "2026-10-05");

        assertThat(saldo(a)).isEqualTo("300.00");
        assertThat(saldo(b)).isEqualTo("200.00");
        assertThat(new BigDecimal(saldo(a)).add(new BigDecimal(saldo(b)))).isEqualByComparingTo("500.00");
    }

    // F04 T2 (CA2)
    @Test
    void transferenciaNaoEntraNoResumoDeRendaEGasto() throws Exception {
        transferir(conta("Corrente", "500.00"), conta("Poupança", "0.00"), "200.00", "2026-10-05");

        mockMvc.perform(get("/api/finance/summary").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$.income").value("0.00"))
                .andExpect(jsonPath("$.expense").value("0.00"));
    }

    // F04 T3 (CA3)
    @Test
    void mesmaContaDeOrigemEDestinoResponde400() throws Exception {
        String a = conta("Corrente", "500.00");
        mockMvc.perform(post("/api/finance/transfers").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":\"10.00\",\"date\":\"2026-10-05\"}".formatted(a, a)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("toAccountId"));
    }

    @Test
    void excluirUmaPernaExcluiAsDuas() throws Exception {
        String a = conta("Corrente", "500.00");
        String b = conta("Poupança", "0.00");
        transferir(a, b, "200.00", "2026-10-05");

        String body = mockMvc.perform(get("/api/finance/transactions").param("accountId", b).with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$[0].transferDirection").value("IN"))
                .andReturn().getResponse().getContentAsString();
        String pernaDeEntrada = JsonPath.read(body, "$[0].id");

        mockMvc.perform(delete("/api/finance/transactions/" + pernaDeEntrada).with(usuario(userId)))
                .andExpect(status().isNoContent());

        assertThat(saldo(a)).isEqualTo("500.00");
        assertThat(saldo(b)).isEqualTo("0.00");
        mockMvc.perform(get("/api/finance/transactions").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void editarATransferenciaAtualizaAsDuasPernas() throws Exception {
        String a = conta("Corrente", "500.00");
        String b = conta("Poupança", "0.00");
        String c = conta("Investimento", "0.00");
        String grupo = transferir(a, b, "200.00", "2026-10-05");

        mockMvc.perform(put("/api/finance/transfers/" + grupo).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":\"150.00\",\"date\":\"2026-10-06\"}".formatted(a, c)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("150.00"));

        assertThat(List.of(saldo(a), saldo(b), saldo(c))).containsExactly("350.00", "0.00", "150.00");
    }

    @Test
    void editarPernaPeloEndpointDeTransacoesResponde400() throws Exception {
        String a = conta("Corrente", "500.00");
        String b = conta("Poupança", "0.00");
        transferir(a, b, "200.00", "2026-10-05");
        String body = mockMvc.perform(get("/api/finance/transactions").param("accountId", a).with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        String perna = JsonPath.read(body, "$[0].id");

        mockMvc.perform(put("/api/finance/transactions/" + perna).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXPENSE\",\"amount\":\"1.00\",\"date\":\"2026-10-05\",\"accountId\":\"%s\",\"categoryId\":\"%s\"}"
                                .formatted(a, categoria("Lazer"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contaDeOutroUsuarioResponde404() throws Exception {
        String minha = conta("Corrente", "500.00");
        String body = mockMvc.perform(post("/api/finance/accounts").with(usuario(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alheia\",\"type\":\"CASH\",\"initialBalance\":\"0.00\"}"))
                .andReturn().getResponse().getContentAsString();
        String alheia = JsonPath.read(body, "$.id");

        mockMvc.perform(post("/api/finance/transfers").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":\"%s\",\"toAccountId\":\"%s\",\"amount\":\"10.00\",\"date\":\"2026-10-05\"}".formatted(minha, alheia)))
                .andExpect(status().isNotFound());
        assertThat(saldo(minha)).isEqualTo("500.00");
    }
}
