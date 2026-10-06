package com.porganization.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porganization.finance.support.FinanceFixture;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class DataResetIT extends FinanceFixture {

    private final UUID outro = UUID.randomUUID();

    @BeforeEach
    void hoje() {
        clock.setInstant(Instant.parse("2026-10-06T15:00:00Z"));
    }

    private ResultActions apagar(String secao) throws Exception {
        return mockMvc.perform(delete("/api/data/" + secao).with(usuario(userId)));
    }

    private int linhas(String tabela, UUID dono) {
        return jdbc.queryForObject("select count(*) from " + tabela + " where user_id = ?", Integer.class, dono);
    }

    private UUID materia(UUID dono) {
        UUID id = jdbc.queryForObject("insert into subjects (user_id, name, priority_order) values (?, 'Cálculo', 1) returning id",
                UUID.class, dono);
        jdbc.update("insert into lessons (user_id, subject_id, title, studied_at, duration_minutes) values (?, ?, 'Limites', now(), 50)",
                dono, id);
        UUID tag = jdbc.queryForObject("insert into tags (user_id, name) values (?, 'prova') returning id", UUID.class, dono);
        jdbc.update("insert into subject_tags (subject_id, tag_id) values (?, ?)", id, tag);
        return id;
    }

    // B14 T1 (CA1)
    @Test
    void apagaSoASecaoPedidaESoDoUsuario() throws Exception {
        postJson("/api/commitments", "{\"title\":\"Dentista\",\"date\":\"2026-10-08\"}");
        postJson("/api/tasks", "{\"title\":\"Beber água\"}");
        materia(userId);
        jdbc.update("insert into commitments (user_id, title, date) values (?, 'Alheio', '2026-10-08')", outro);
        materia(outro);

        apagar("commitments").andExpect(status().isNoContent());
        assertThat(linhas("commitments", userId)).isZero();
        assertThat(linhas("commitments", outro)).isEqualTo(1);
        assertThat(linhas("daily_tasks", userId)).isEqualTo(1);

        apagar("tasks").andExpect(status().isNoContent());
        assertThat(linhas("daily_tasks", userId)).isZero();
        assertThat(linhas("subjects", userId)).isEqualTo(1);

        apagar("studies").andExpect(status().isNoContent());
        assertThat(linhas("subjects", userId)).isZero();
        assertThat(linhas("lessons", userId)).isZero();
        assertThat(linhas("tags", userId)).isZero();
        assertThat(linhas("subjects", outro)).isEqualTo(1);
        assertThat(linhas("lessons", outro)).isEqualTo(1);
    }

    // B14 T2 (CA2)
    @Test
    void financasApagaTudoMesmoComFaturaEAgendadoEAsCategoriasPadraoVoltam() throws Exception {
        String conta = conta("Corrente", "100.00");
        String cartao = postJson("/api/finance/cards", """
                {"name":"Nubank","creditLimit":"3000.00","closingDay":5,"dueDay":12,"paymentAccountId":"%s"}
                """.formatted(conta));
        postJson("/api/finance/cards/" + cartao + "/purchases", """
                {"amount":"300.00","date":"2026-10-06","description":"Celular","categoryId":"%s","installments":3}
                """.formatted(categoria("Lazer")));
        transacao("EXPENSE", "20.00", "2026-10-06", conta, categoria("Alimentação"), true, tag("viagem"));
        postJson("/api/finance/recurring", """
                {"type":"EXPENSE","amount":"39.90","description":"Netflix","cardId":"%s","categoryId":"%s","dayOfMonth":1,"startMonth":"2026-10"}
                """.formatted(cartao, categoria("Lazer")));
        jdbc.update("insert into accounts (user_id, name, type) values (?, 'Alheia', 'CASH')", outro);

        apagar("finance").andExpect(status().isNoContent());

        for (String tabela : new String[] {"transactions", "recurring_transactions", "card_statements", "credit_cards", "accounts",
                "categories", "finance_tags"}) {
            assertThat(linhas(tabela, userId)).as(tabela).isZero();
        }
        assertThat(linhas("accounts", outro)).isEqualTo(1);
        mockMvc.perform(get("/api/finance/accounts").with(usuario(userId))).andExpect(jsonPath("$.length()").value(0));
        // Como no primeiro acesso: as categorias padrão são criadas de novo
        mockMvc.perform(get("/api/finance/categories").with(usuario(userId))).andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    // B14 T3 (CA1)
    @Test
    void secaoDesconhecidaDa404ESemLoginDa401() throws Exception {
        apagar("tudo").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/data/tasks")).andExpect(status().isUnauthorized());
    }
}
