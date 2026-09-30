package com.porganization.finance.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.finance.support.FinanceFixture;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ScheduledIT extends FinanceFixture {

    private String conta;
    private String salarioId;

    /** Hoje é 07/10/2026 (5º dia útil de outubro) e o salário de 3200,00 cai hoje. */
    @BeforeEach
    void salario() throws Exception {
        clock.setInstant(Instant.parse("2026-10-07T15:00:00Z"));
        conta = conta("Bradesco", "1000.00");
        postJson("/api/finance/recurring", """
                {"type":"INCOME","amount":"3200.00","description":"Salário","accountId":"%s","categoryId":"%s",
                 "startMonth":"2026-09","ruleType":"BUSINESS_DAY","businessDay":5}
                """.formatted(conta, categoria("Salário")));
        String lista = mockMvc.perform(get("/api/finance/scheduled").param("month", "2026-10").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("TO_CONFIRM"))
                .andExpect(jsonPath("$[0].date").value("2026-10-07"))
                .andExpect(jsonPath("$[0].accountName").value("Bradesco"))
                .andReturn().getResponse().getContentAsString();
        salarioId = JsonPath.read(lista, "$[0].id");
    }

    private ResultActions acao(String acao, String corpo) throws Exception {
        var request = post("/api/finance/scheduled/" + salarioId + "/" + acao).with(usuario(userId));
        if (corpo != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(request);
    }

    // F19 T1 (CA1)
    @Test
    void confirmarComValorEDataAjustadosMudaSoAOcorrencia() throws Exception {
        acao("confirm", "{\"amount\":\"3180.00\",\"date\":\"2026-10-06\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.amount").value("3180.00"))
                .andExpect(jsonPath("$.expectedAmount").value("3200.00"))
                .andExpect(jsonPath("$.date").value("2026-10-06"))
                .andExpect(jsonPath("$.scheduledDate").value("2026-10-07"));

        assertThat(saldo(conta)).isEqualTo("4180.00");
        mockMvc.perform(get("/api/finance/recurring").with(usuario(userId)))
                .andExpect(jsonPath("$[0].amount").value("3200.00"));
    }

    @Test
    void confirmarSemCorpoUsaOValorPrevistoEHoje() throws Exception {
        acao("confirm", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("3200.00"))
                .andExpect(jsonPath("$.date").value("2026-10-07"));
        assertThat(saldo(conta)).isEqualTo("4200.00");

        acao("confirm", "{\"date\":\"2026-10-08\"}").andExpect(status().isConflict());
    }

    @Test
    void dataDeConfirmacaoNoFuturoResponde400() throws Exception {
        acao("confirm", "{\"date\":\"2026-10-08\"}").andExpect(status().isBadRequest());
    }

    // F19 T2 (CA2)
    @Test
    void remarcarMudaSoADataEAvisaQuandoPassaDaProximaOcorrencia() throws Exception {
        acao("reschedule", "{\"date\":\"2026-10-10\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrence.status").value("RESCHEDULED"))
                .andExpect(jsonPath("$.occurrence.date").value("2026-10-10"))
                .andExpect(jsonPath("$.occurrence.scheduledDate").value("2026-10-07"))
                .andExpect(jsonPath("$.warning").doesNotExist());

        acao("reschedule", "{\"date\":\"2026-10-07\"}").andExpect(status().isBadRequest());

        // Novembro: 5º dia útil é 09/11
        acao("reschedule", "{\"date\":\"2026-11-10\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning").value("A nova data passa da próxima ocorrência (09/11/2026)."));

        // Continua listada no mês de origem
        mockMvc.perform(get("/api/finance/scheduled").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$[0].status").value("RESCHEDULED"));
        assertThat(saldo(conta)).isEqualTo("1000.00");
    }

    // F19 T3 (CA3)
    @Test
    void cancelarTiraDoMesSemApagarOHistoricoEAgirDeNovoResponde409() throws Exception {
        acao("skip", null).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/finance/scheduled").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].description").value("Salário"));
        // Fora do extrato e dos totais; o mês não é gerado de novo
        mockMvc.perform(get("/api/finance/transactions").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/finance/summary").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$.income").value("0.00"));

        acao("confirm", null).andExpect(status().isConflict());
        acao("skip", null).andExpect(status().isConflict());
    }

    @Test
    void estadosDoMesSeguemADataDeHoje() throws Exception {
        clock.setInstant(Instant.parse("2026-10-08T15:00:00Z"));
        mockMvc.perform(get("/api/finance/scheduled").param("month", "2026-10").with(usuario(userId)))
                .andExpect(jsonPath("$[0].status").value("OVERDUE"));
        mockMvc.perform(get("/api/finance/scheduled").param("month", "2026-11").with(usuario(userId)))
                .andExpect(jsonPath("$[*].status").value(contains("EXPECTED")))
                .andExpect(jsonPath("$[0].date").value("2026-11-09"));
    }

    @Test
    void ocorrenciaDeOutroUsuarioOuLancamentoComumResponde404() throws Exception {
        mockMvc.perform(post("/api/finance/scheduled/" + salarioId + "/confirm").with(usuario(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        String comum = transacao("EXPENSE", "50.00", "2026-10-07", conta, categoria("Lazer"), false);
        mockMvc.perform(post("/api/finance/scheduled/" + comum + "/confirm").with(usuario(userId)))
                .andExpect(status().isNotFound());
    }
}
