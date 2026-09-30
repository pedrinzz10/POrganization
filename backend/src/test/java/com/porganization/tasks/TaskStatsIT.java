package com.porganization.tasks;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TaskStatsIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();

    private String tarefa(String titulo) throws Exception {
        String body = mockMvc.perform(post("/api/tasks").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + titulo + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // T02 T3 (CA3)
    @Test
    void cincoDeDezDiasDaCinquentaPorCentoECriadaHojeDaNulo() throws Exception {
        clock.setInstant(Instant.parse("2026-10-05T15:00:00Z"));
        String ler = tarefa("Ler");
        clock.setInstant(Instant.parse("2026-10-15T15:00:00Z"));
        // Feita em 5 dos 10 dias anteriores (05 a 14); marcações antigas gravadas direto, fora da janela de 7 dias
        for (String dia : new String[] {"2026-10-05", "2026-10-06", "2026-10-07", "2026-10-13", "2026-10-14"}) {
            jdbc.update("insert into daily_task_completions (task_id, user_id, day) values (?::uuid, ?, ?)", ler, userId,
                    java.sql.Date.valueOf(LocalDate.parse(dia)));
        }
        String nova = tarefa("Nova");

        mockMvc.perform(get("/api/tasks/stats").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.taskId == '%s')].completionRate".formatted(ler)).value(org.hamcrest.Matchers.contains("50.00")))
                .andExpect(jsonPath("$[?(@.taskId == '%s')].streak".formatted(ler)).value(org.hamcrest.Matchers.contains(2)))
                .andExpect(jsonPath("$[?(@.taskId == '%s')].completionRate".formatted(nova)).value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$[?(@.taskId == '%s')].streak".formatted(nova)).value(org.hamcrest.Matchers.contains(0)));

        // Marcando hoje, a sequência sobe e hoje passa a contar: 6 de 11
        mockMvc.perform(put("/api/tasks/" + ler + "/completions/2026-10-15").with(usuario(userId))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/stats").with(usuario(userId)))
                .andExpect(jsonPath("$[?(@.taskId == '%s')].streak".formatted(ler)).value(org.hamcrest.Matchers.contains(3)))
                .andExpect(jsonPath("$[?(@.taskId == '%s')].completionRate".formatted(ler)).value(org.hamcrest.Matchers.contains("54.54")));
    }
}
