package com.porganization.studies;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class StudyStatsIT extends IntegrationTest {

    @Autowired
    private SubjectRepository subjects;

    private final UUID userId = UUID.randomUUID();
    private UUID java;

    @BeforeEach
    void materia() {
        Subject subject = new Subject(userId, "Java", 1);
        subject.setSessionsPerWeek(3);
        java = subjects.saveAndFlush(subject).getId();
    }

    /** Sessão de aula de "minutos" minutos começando em "inicio"; terminada ou abandonada. */
    private void sessao(Instant inicio, int minutos, boolean terminar, String titulo) throws Exception {
        clock.setInstant(inicio);
        String body = mockMvc.perform(post("/api/study/sessions").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"" + java + "\",\"type\":\"LESSON\"}"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        clock.setInstant(inicio.plusSeconds(minutos * 60L));
        String acao = terminar ? "/finish" : "/abandon";
        mockMvc.perform(post("/api/study/sessions/" + id + acao).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content(terminar ? "{\"title\":\"" + titulo + "\"}" : "{}"))
                .andExpect(status().isOk());
    }

    // E10 T1 (CA1)
    @Test
    void minutosSomamSoSessoesTerminadas() throws Exception {
        sessao(Instant.parse("2026-10-01T12:00:00Z"), 50, true, "Streams");
        sessao(Instant.parse("2026-10-02T12:00:00Z"), 25, true, "Lambdas");
        sessao(Instant.parse("2026-10-02T15:00:00Z"), 30, false, null);

        clock.setInstant(Instant.parse("2026-10-02T20:00:00Z"));
        mockMvc.perform(get("/api/study/stats").param("from", "2026-09-28").param("to", "2026-10-04").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes").value(75))
                .andExpect(jsonPath("$.subjects", hasSize(1)))
                .andExpect(jsonPath("$.subjects[0].name").value("Java"))
                .andExpect(jsonPath("$.subjects[0].minutes").value(75))
                .andExpect(jsonPath("$.subjects[0].lessons").value(2))
                .andExpect(jsonPath("$.subjects[0].sessionsThisWeek").value(2))
                .andExpect(jsonPath("$.subjects[0].sessionsPerWeek").value(3))
                .andExpect(jsonPath("$.weeks[*].minutes").value(contains(75)))
                .andExpect(jsonPath("$.lessons[*].title").value(contains("Lambdas", "Streams")));
    }

    @Test
    void foraDoIntervaloNaoConta() throws Exception {
        sessao(Instant.parse("2026-09-20T12:00:00Z"), 40, true, "Antiga");
        clock.setInstant(Instant.parse("2026-10-02T20:00:00Z"));

        mockMvc.perform(get("/api/study/stats").param("from", "2026-09-28").param("to", "2026-10-04").with(usuario(userId)))
                .andExpect(jsonPath("$.totalMinutes").value(0))
                .andExpect(jsonPath("$.subjects[0].minutes").value(0))
                .andExpect(jsonPath("$.lessons", hasSize(0)));
    }

    @Test
    void minutosPorSemanaIncluemSemanasVazias() throws Exception {
        sessao(Instant.parse("2026-09-22T12:00:00Z"), 20, true, "Semana 1");
        sessao(Instant.parse("2026-10-06T12:00:00Z"), 30, true, "Semana 3");
        clock.setInstant(Instant.parse("2026-10-06T20:00:00Z"));

        mockMvc.perform(get("/api/study/stats").param("from", "2026-09-21").param("to", "2026-10-11").with(usuario(userId)))
                .andExpect(jsonPath("$.weeks[*].weekStart").value(contains("2026-09-21", "2026-09-28", "2026-10-05")))
                .andExpect(jsonPath("$.weeks[*].minutes").value(contains(20, 0, 30)));
    }

    @Test
    void intervaloInvalidoResponde400() throws Exception {
        mockMvc.perform(get("/api/study/stats").param("from", "2026-10-10").param("to", "2026-10-01").with(usuario(userId)))
                .andExpect(status().isBadRequest());
    }
}
