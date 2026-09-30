package com.porganization.finance.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.finance.support.FinanceFixture;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Editar ou excluir um agendado leva a mudança para as ocorrências em aberto (F22): antes, a data e
 * o valor antigos ficavam no mês já gerado, e excluir + recriar deixava o mês em dobro.
 */
class RecurringEditIT extends FinanceFixture {

    private static final String REGRA = """
            {"type":"INCOME","amount":"%s","description":"Salário","accountId":"%s","categoryId":"%s",
             "startMonth":"%s","ruleType":"BUSINESS_DAY","businessDay":%d}
            """;

    private String conta;
    private String salario;
    private String modelo;

    /** Hoje é quarta 02/09/2026; o 5º dia útil de setembro é 08/09 (07/09 é feriado). */
    @BeforeEach
    void salario() throws Exception {
        clock.setInstant(Instant.parse("2026-09-02T15:00:00Z"));
        conta = conta("Bradesco", "0.00");
        salario = categoria("Salário");
        modelo = postJson("/api/finance/recurring", REGRA.formatted("1000.00", conta, salario, "2026-09", 5));
        assertThat(ocorrencias("2026-09")).containsExactly(List.of("2026-09-08", "2026-09-08", "1000.00", "EXPECTED"));
        assertThat(ocorrencias("2026-10")).containsExactly(List.of("2026-10-07", "2026-10-07", "1000.00", "EXPECTED"));
    }

    /** [data, data prevista, valor, status] de cada ocorrência do mês. */
    private List<List<String>> ocorrencias(String mes) throws Exception {
        String body = mockMvc.perform(get("/api/finance/scheduled").param("month", mes).with(usuario(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> lista = JsonPath.read(body, "$");
        return lista.stream()
                .map(o -> List.of((String) o.get("date"), (String) o.get("scheduledDate"), (String) o.get("amount"), (String) o.get("status")))
                .toList();
    }

    private String idDaOcorrencia(String mes) throws Exception {
        String body = mockMvc.perform(get("/api/finance/scheduled").param("month", mes).with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    private void editar(String valor, String inicio, int diaUtil) throws Exception {
        mockMvc.perform(put("/api/finance/recurring/" + modelo).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content(REGRA.formatted(valor, conta, salario, inicio, diaUtil)))
                .andExpect(status().isOk());
    }

    // F22 T1 (CA1)
    @Test
    void editarRegraEValorMudaAsOcorrenciasEmAberto() throws Exception {
        editar("1100.00", "2026-09", 3);

        assertThat(ocorrencias("2026-09")).containsExactly(List.of("2026-09-03", "2026-09-03", "1100.00", "EXPECTED"));
        assertThat(ocorrencias("2026-10")).containsExactly(List.of("2026-10-05", "2026-10-05", "1100.00", "EXPECTED"));
    }

    @Test
    void pagaNaoMudaERemarcadaMantemADataEscolhida() throws Exception {
        clock.setInstant(Instant.parse("2026-09-08T15:00:00Z"));
        mockMvc.perform(post("/api/finance/scheduled/" + idDaOcorrencia("2026-09") + "/confirm").with(usuario(userId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/finance/scheduled/" + idDaOcorrencia("2026-10") + "/reschedule").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-10-20\"}"))
                .andExpect(status().isOk());

        editar("1100.00", "2026-09", 3);

        assertThat(ocorrencias("2026-09")).containsExactly(List.of("2026-09-08", "2026-09-08", "1000.00", "CONFIRMED"));
        assertThat(ocorrencias("2026-10")).containsExactly(List.of("2026-10-20", "2026-10-05", "1100.00", "RESCHEDULED"));
        assertThat(saldo(conta)).isEqualTo("1000.00");
    }

    // F22 T2 (CA2)
    @Test
    void mudarOInicioTiraOMesQueSaiuEDevolveSeVoltar() throws Exception {
        editar("1000.00", "2026-10", 5);
        assertThat(ocorrencias("2026-09")).isEmpty();
        assertThat(ocorrencias("2026-10")).hasSize(1);

        editar("1000.00", "2026-09", 5);
        assertThat(ocorrencias("2026-09")).containsExactly(List.of("2026-09-08", "2026-09-08", "1000.00", "EXPECTED"));
        assertThat(ocorrencias("2026-10")).hasSize(1);
    }

    // F22 T3 (CA3)
    @Test
    void excluirERecriarNaoDuplica() throws Exception {
        clock.setInstant(Instant.parse("2026-09-08T15:00:00Z"));
        mockMvc.perform(post("/api/finance/scheduled/" + idDaOcorrencia("2026-09") + "/confirm").with(usuario(userId)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/finance/recurring/" + modelo).with(usuario(userId))).andExpect(status().isNoContent());

        // O recebido fica no extrato; outubro, que estava em aberto, sai junto com o modelo
        String extrato = mockMvc.perform(get("/api/finance/transactions").param("accountId", conta).with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        List<String> valores = JsonPath.read(extrato, "$[*].amount");
        assertThat(valores).containsExactly("1000.00");
        assertThat(saldo(conta)).isEqualTo("1000.00");

        postJson("/api/finance/recurring", REGRA.formatted("1000.00", conta, salario, "2026-10", 5));
        assertThat(ocorrencias("2026-10")).containsExactly(List.of("2026-10-07", "2026-10-07", "1000.00", "EXPECTED"));
    }
}
