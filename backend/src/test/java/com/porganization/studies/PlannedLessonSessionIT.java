package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Estudar a aula do plano (E16). Hoje: quinta 01/10/2026. */
class PlannedLessonSessionIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();
    private String java;
    private List<String> aulas;

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @BeforeEach
    void materia() throws Exception {
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));
        String body = postJson("/api/subjects", "{\"name\":\"Java\",\"sessionsPerWeek\":3,\"lessonMode\":\"PLANNED\"}")
                .andReturn().getResponse().getContentAsString();
        java = JsonPath.read(body, "$.id");
        body = postJson("/api/subjects/" + java + "/planned-lessons", "{\"titles\":[\"Variáveis\",\"Laços\",\"Classes\"]}")
                .andReturn().getResponse().getContentAsString();
        aulas = JsonPath.read(body, "$[*].id");
    }

    private String iniciar(String plannedLessonId) throws Exception {
        String extra = plannedLessonId == null ? "" : ",\"plannedLessonId\":\"" + plannedLessonId + "\"";
        String body = postJson("/api/study/sessions", "{\"subjectId\":\"" + java + "\",\"type\":\"LESSON\"" + extra + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void terminar(String sessao, String json) throws Exception {
        clock.setInstant(clock.instant().plusSeconds(50 * 60));
        postJson("/api/study/sessions/" + sessao + "/finish", json).andExpect(status().isOk());
    }

    // E16 T1 (CA1)
    @Test
    void hojeSugereAProximaAulaDaListaEEstudarMarcaComoFeita() throws Exception {
        mockMvc.perform(get("/api/study/today").with(usuario(userId)))
                .andExpect(jsonPath("$.lessons[0].plannedLessonId").value(aulas.get(0)))
                .andExpect(jsonPath("$.lessons[0].plannedLessonTitle").value("Variáveis"));

        // Sem escolher, a sessão pega a próxima pendente e devolve o título pronto
        String sessao = iniciar(null);
        mockMvc.perform(get("/api/study/sessions/active").with(usuario(userId)))
                .andExpect(jsonPath("$.plannedLessonId").value(aulas.get(0)))
                .andExpect(jsonPath("$.plannedLessonTitle").value("Variáveis"));
        // Sem título no fim: vale o nome da aula da lista
        terminar(sessao, "{\"notes\":\"tipos primitivos\"}");

        mockMvc.perform(get("/api/subjects/" + java + "/planned-lessons").with(usuario(userId)))
                .andExpect(jsonPath("$[0].lessonId").isNotEmpty())
                .andExpect(jsonPath("$[1].lessonId").doesNotExist());
        mockMvc.perform(get("/api/subjects").with(usuario(userId))).andExpect(jsonPath("$[0].plannedDone").value(1));
        mockMvc.perform(get("/api/study/stats").param("from", "2026-10-01").param("to", "2026-10-01").with(usuario(userId)))
                .andExpect(jsonPath("$.lessons[0].title").value("Variáveis"));
        // A próxima sugerida passa a ser Laços
        mockMvc.perform(get("/api/study/today").with(usuario(userId)))
                .andExpect(jsonPath("$.lessons[0].plannedLessonTitle").value("Laços"));
    }

    // E16 T2 (CA2)
    @Test
    void escolherOutraAulaDaListaEAJaEstudadaDa409() throws Exception {
        String sessao = iniciar(aulas.get(2));
        terminar(sessao, "{\"title\":\"Classes e objetos\"}");
        mockMvc.perform(get("/api/subjects/" + java + "/planned-lessons").with(usuario(userId)))
                .andExpect(jsonPath("$[2].lessonId").isNotEmpty())
                .andExpect(jsonPath("$[0].lessonId").doesNotExist());

        postJson("/api/study/sessions", "{\"subjectId\":\"%s\",\"type\":\"LESSON\",\"plannedLessonId\":\"%s\"}".formatted(java, aulas.get(2)))
                .andExpect(status().isConflict());
        postJson("/api/study/sessions", "{\"subjectId\":\"%s\",\"type\":\"LESSON\",\"plannedLessonId\":\"%s\"}"
                .formatted(java, UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // E16 T3 (CA3)
    @Test
    void comPlanoNaSemanaHojeMostraSoAsAulasDoPlano() throws Exception {
        String ingles = JsonPath.read(postJson("/api/subjects", "{\"name\":\"Inglês\",\"sessionsPerWeek\":3}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        // Plano: só Inglês hoje
        postJson("/api/study/week/generate?week=2026-10-01", "");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/study/week/slots")
                .param("subjectId", java).param("day", "2026-10-01").with(usuario(userId)));
        postJson("/api/study/week/slots", "{\"subjectId\":\"%s\",\"day\":\"2026-10-01\"}".formatted(ingles));

        mockMvc.perform(get("/api/study/today").with(usuario(userId)))
                .andExpect(jsonPath("$.lessons.length()").value(1))
                .andExpect(jsonPath("$.lessons[0].subjectName").value("Inglês"))
                .andExpect(jsonPath("$.lessons[0].plannedLessonId").doesNotExist());
    }
}
