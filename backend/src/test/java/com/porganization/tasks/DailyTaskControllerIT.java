package com.porganization.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class DailyTaskControllerIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();

    /** Meio-dia em São Paulo no dia informado. */
    private void hoje(String dia) {
        clock.setInstant(Instant.parse(dia + "T15:00:00Z"));
    }

    private String tarefa(String json) throws Exception {
        String body = mockMvc.perform(post("/api/tasks").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions dia(String dia) throws Exception {
        return mockMvc.perform(get("/api/tasks/day").param("date", dia).with(usuario(userId)));
    }

    private ResultActions marcar(String id, String dia) throws Exception {
        return mockMvc.perform(put("/api/tasks/" + id + "/completions/" + dia).with(usuario(userId)));
    }

    private int marcacoes(String id) {
        return jdbc.queryForObject("select count(*) from daily_task_completions where task_id = ?::uuid", Integer.class, id);
    }

    // T01 T1 (CA1)
    @Test
    void semDiasValeTodoDiaComSegQuaSexSoNessesDias() throws Exception {
        hoje("2026-10-05");
        tarefa("{\"title\":\"Beber água\",\"emoji\":\"💧\"}");
        tarefa("{\"title\":\"Academia\",\"weekDays\":[\"MON\",\"WED\",\"FRI\"]}");

        dia("2026-10-06").andExpect(jsonPath("$[*].title").value(contains("Beber água")));
        dia("2026-10-07").andExpect(jsonPath("$[*].title").value(contains("Beber água", "Academia")))
                .andExpect(jsonPath("$[0].emoji").value("💧"))
                .andExpect(jsonPath("$[0].done").value(false));
        mockMvc.perform(get("/api/tasks").with(usuario(userId)))
                .andExpect(jsonPath("$[0].weekDays.length()").value(7))
                .andExpect(jsonPath("$[1].weekDays").value(containsInAnyOrder("MON", "WED", "FRI")));
    }

    // T01 T2 (CA2)
    @Test
    void criadaHojeJaApareceHojeENuncaAntes() throws Exception {
        hoje("2026-10-07");
        tarefa("{\"title\":\"Ler 20 min\"}");

        dia("2026-10-07").andExpect(jsonPath("$[*].title").value(contains("Ler 20 min")));
        dia("2026-10-06").andExpect(jsonPath("$.length()").value(0));
        // Sem ?date, é hoje
        mockMvc.perform(get("/api/tasks/day").with(usuario(userId))).andExpect(jsonPath("$.length()").value(1));
    }

    // T01 T3 (CA3)
    @Test
    void marcarEDesmarcarSaoIdempotentesEForaDaJanelaResponde400() throws Exception {
        hoje("2026-10-05");
        String agua = tarefa("{\"title\":\"Beber água\"}");
        String academia = tarefa("{\"title\":\"Academia\",\"weekDays\":[\"MON\",\"WED\",\"FRI\"]}");
        hoje("2026-10-14");

        marcar(agua, "2026-10-14").andExpect(status().isNoContent());
        marcar(agua, "2026-10-14").andExpect(status().isNoContent());
        assertThat(marcacoes(agua)).isEqualTo(1);
        dia("2026-10-14").andExpect(jsonPath("$[0].done").value(true));

        mockMvc.perform(delete("/api/tasks/" + agua + "/completions/2026-10-14").with(usuario(userId))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/tasks/" + agua + "/completions/2026-10-14").with(usuario(userId))).andExpect(status().isNoContent());
        assertThat(marcacoes(agua)).isZero();

        marcar(agua, "2026-10-07").andExpect(status().isNoContent());      // 7 dias atrás: vale
        marcar(agua, "2026-10-06").andExpect(status().isBadRequest());     // 8 dias atrás
        marcar(agua, "2026-10-15").andExpect(status().isBadRequest());     // amanhã
        marcar(academia, "2026-10-13").andExpect(status().isBadRequest()); // terça: não é dia dela
        marcar(academia, "2026-10-12").andExpect(status().isNoContent());  // segunda

        hoje("2026-10-10");
        String nova = tarefa("{\"title\":\"Nova\"}");
        marcar(nova, "2026-10-09").andExpect(status().isBadRequest());     // antes da criação
    }

    @Test
    void mudarOsDiasValeDeHojeEmDiante() throws Exception {
        hoje("2026-10-05");
        String academia = tarefa("{\"title\":\"Academia\",\"weekDays\":[\"MON\",\"WED\",\"FRI\"]}");
        hoje("2026-10-08");

        mockMvc.perform(put("/api/tasks/" + academia).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Academia\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekDays.length()").value(7));

        dia("2026-10-06").andExpect(jsonPath("$.length()").value(0));        // terça passada: regra antiga
        dia("2026-10-13").andExpect(jsonPath("$[*].title").value(contains("Academia"))); // terça que vem: todo dia
    }

    // T01 T5 (CA5)
    @Test
    void arquivarEsconderMantemHistoricoExcluirApagaTudoOutroUsuario404() throws Exception {
        hoje("2026-10-07");
        String ler = tarefa("{\"title\":\"Ler\"}");
        marcar(ler, "2026-10-07").andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/tasks/" + ler).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archived\":true}"))
                .andExpect(jsonPath("$.archived").value(true));
        dia("2026-10-07").andExpect(jsonPath("$.length()").value(0));
        assertThat(marcacoes(ler)).isEqualTo(1);

        mockMvc.perform(get("/api/tasks/" + ler).with(usuario(UUID.randomUUID()))).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/tasks/" + ler).with(usuario(userId))).andExpect(status().isNoContent());
        assertThat(marcacoes(ler)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from daily_task_schedules where task_id = ?::uuid", Integer.class, ler)).isZero();
    }

    @Test
    void reordenarGravaAOrdemEDiasVaziosResponde400() throws Exception {
        hoje("2026-10-07");
        String a = tarefa("{\"title\":\"A\"}");
        String b = tarefa("{\"title\":\"B\"}");
        String c = tarefa("{\"title\":\"C\"}");

        mockMvc.perform(put("/api/tasks/order").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"%s\",\"%s\",\"%s\"]}".formatted(c, a, b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains("C", "A", "B")));
        dia("2026-10-07").andExpect(jsonPath("$[*].title").value(contains("C", "A", "B")));

        mockMvc.perform(post("/api/tasks").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"weekDays\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
