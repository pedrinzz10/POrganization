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

class StudyTodayControllerIT extends IntegrationTest {

    @Autowired
    private SubjectRepository subjects;

    private final UUID userId = UUID.randomUUID();
    private UUID ingles;
    private UUID java;

    @BeforeEach
    void materias() {
        ingles = subjects.saveAndFlush(new Subject(userId, "Inglês", 1)).getId();
        java = subjects.saveAndFlush(new Subject(userId, "Java", 2)).getId();
    }

    private void estudarAula(UUID materia, Instant inicio, String titulo) throws Exception {
        clock.setInstant(inicio);
        String body = mockMvc.perform(post("/api/study/sessions").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"" + materia + "\",\"type\":\"LESSON\"}"))
                .andReturn().getResponse().getContentAsString();
        String sessao = JsonPath.read(body, "$.id");
        clock.setInstant(inicio.plusSeconds(3000));
        mockMvc.perform(post("/api/study/sessions/" + sessao + "/finish").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + titulo + "\"}"))
                .andExpect(status().isOk());
    }

    // E08 T3 (CA3)
    @Test
    void aulasSugeridasSeguemAPrioridade() throws Exception {
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));

        mockMvc.perform(get("/api/study/today").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-01"))
                .andExpect(jsonPath("$.reviews", hasSize(0)))
                .andExpect(jsonPath("$.lessons[*].subjectName").value(contains("Inglês", "Java")))
                .andExpect(jsonPath("$.lessons[0].suggestedMinutes").value(50));
    }

    @Test
    void revisaoVencidaAparecePrimeiroEAMetaDaSemanaEContada() throws Exception {
        // segunda 28/09: duas aulas de Java (meta 2) -> Java sai das sugestões na semana
        estudarAula(java, Instant.parse("2026-09-28T12:00:00Z"), "Streams");
        estudarAula(java, Instant.parse("2026-09-28T14:00:00Z"), "Lambdas");

        // quinta 01/10: as duas revisões (vencidas em 29/09) aparecem, mais atrasadas primeiro
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));
        mockMvc.perform(get("/api/study/today").with(usuario(userId)))
                .andExpect(jsonPath("$.reviews[*].lessonTitle").value(contains("Streams", "Lambdas")))
                .andExpect(jsonPath("$.reviews[0].daysOverdue").value(2))
                .andExpect(jsonPath("$.reviews[0].subjectName").value("Java"))
                .andExpect(jsonPath("$.lessons[*].subjectName").value(contains("Inglês")));

        // na semana seguinte a meta zera e Java volta a ser sugerido
        clock.setInstant(Instant.parse("2026-10-05T12:00:00Z"));
        mockMvc.perform(get("/api/study/today").with(usuario(userId)))
                .andExpect(jsonPath("$.lessons[*].subjectName").value(contains("Inglês", "Java")));
    }

    @Test
    void naoMostraMateriasDeOutroUsuario() throws Exception {
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));
        mockMvc.perform(get("/api/study/today").with(usuario(UUID.randomUUID())))
                .andExpect(jsonPath("$.lessons", hasSize(0)));
    }
}
