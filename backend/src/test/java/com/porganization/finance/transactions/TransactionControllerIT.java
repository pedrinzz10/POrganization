package com.porganization.finance.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porganization.finance.support.FinanceFixture;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TransactionControllerIT extends FinanceFixture {

    private static final String BASE = "/api/finance/transactions";

    // F03 T1 (CA1)
    @Test
    void valorPrecisaSerPositivoECategoriaDoMesmoTipo() throws Exception {
        String conta = conta("Nubank", "0.00");
        String lazer = categoria("Lazer");
        String salario = categoria("Salário");

        mockMvc.perform(post(BASE).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"EXPENSE","amount":"0.00","date":"2026-10-01","accountId":"%s","categoryId":"%s"}
                        """.formatted(conta, lazer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"));

        mockMvc.perform(post(BASE).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"EXPENSE","amount":"10.00","date":"2026-10-01","accountId":"%s","categoryId":"%s"}
                        """.formatted(conta, salario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));
    }

    // F03 T2 (CA2)
    @Test
    void saldoEInicialMaisRendasPagasMenosGastosPagos() throws Exception {
        String conta = conta("Nubank", "1000.00");
        transacao("INCOME", "2500.00", "2026-10-05", conta, categoria("Salário"), true);
        transacao("EXPENSE", "300.55", "2026-10-06", conta, categoria("Alimentação"), true);
        transacao("EXPENSE", "100.00", "2026-10-07", conta, categoria("Lazer"), false);

        assertThat(saldo(conta)).isEqualTo("3199.45");
    }

    // F03 T3 (CA3)
    @Test
    void filtrosCombinadosDevolvemSoOQueCasaComTodos() throws Exception {
        String conta = conta("Nubank", "0.00");
        String lazer = categoria("Lazer");
        String alimentacao = categoria("Alimentação");
        String viagem = tag("viagem");

        transacao("EXPENSE", "200.00", "2026-10-10", conta, lazer, true, viagem);      // casa com tudo
        transacao("EXPENSE", "50.00", "2026-10-11", conta, lazer, true);               // sem a tag
        transacao("EXPENSE", "80.00", "2026-10-12", conta, alimentacao, true, viagem); // outra categoria
        transacao("EXPENSE", "90.00", "2026-09-30", conta, lazer, true, viagem);       // outro mês

        mockMvc.perform(get(BASE).param("month", "2026-10").param("categoryId", lazer).param("tagId", viagem)
                        .with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amount").value("200.00"))
                .andExpect(jsonPath("$[0].categoryName").value("Lazer"))
                .andExpect(jsonPath("$[0].tags[0].name").value("viagem"));

        mockMvc.perform(get(BASE).param("month", "2026-10").with(usuario(userId))).andExpect(jsonPath("$", hasSize(3)));
    }

    // F03 T4 (CA4, completa o CA2 da F01)
    @Test
    void contaComTransacaoNaoPodeSerExcluida() throws Exception {
        String conta = conta("Nubank", "0.00");
        transacao("EXPENSE", "10.00", "2026-10-01", conta, categoria("Lazer"), true);

        mockMvc.perform(delete("/api/finance/accounts/" + conta).with(usuario(userId)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/finance/accounts/" + conta).with(usuario(userId))).andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/finance/accounts/" + conta)
                        .with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content("{\"archived\":true}"))
                .andExpect(status().isOk());
    }

    // F03 T5 (CA5, completa o CA2 da F02)
    @Test
    void categoriaEmUsoNaoPodeSerExcluida() throws Exception {
        String lazer = categoria("Lazer");
        transacao("EXPENSE", "10.00", "2026-10-01", conta("Nubank", "0.00"), lazer, true);

        mockMvc.perform(delete("/api/finance/categories/" + lazer).with(usuario(userId))).andExpect(status().isConflict());
    }

    @Test
    void resumoDoMesSomaRendasEGastos() throws Exception {
        String conta = conta("Nubank", "0.00");
        transacao("INCOME", "2500.00", "2026-10-05", conta, categoria("Salário"), true);
        transacao("EXPENSE", "300.55", "2026-10-06", conta, categoria("Alimentação"), true);
        transacao("EXPENSE", "100.00", "2026-10-07", conta, categoria("Lazer"), false);
        transacao("EXPENSE", "999.00", "2026-11-01", conta, categoria("Lazer"), true);

        mockMvc.perform(get("/api/finance/summary").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$.income").value("2500.00"))
                .andExpect(jsonPath("$.expense").value("400.55"))
                .andExpect(jsonPath("$.net").value("2099.45"));
    }

    @Test
    void contaOuCategoriaDeOutroUsuarioNaoServem() throws Exception {
        String minhaConta = conta("Nubank", "0.00");
        String lazer = categoria("Lazer");
        UUID outro = UUID.randomUUID();

        mockMvc.perform(post(BASE).with(usuario(outro)).contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"EXPENSE","amount":"10.00","date":"2026-10-01","accountId":"%s","categoryId":"%s"}
                        """.formatted(minhaConta, lazer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirTransacaoVoltaOSaldo() throws Exception {
        String conta = conta("Nubank", "100.00");
        String id = transacao("EXPENSE", "30.00", "2026-10-01", conta, categoria("Lazer"), true);
        assertThat(saldo(conta)).isEqualTo("70.00");

        mockMvc.perform(delete(BASE + "/" + id).with(usuario(userId))).andExpect(status().isNoContent());
        assertThat(saldo(conta)).isEqualTo("100.00");
    }
}
