package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class StudyCalendarIT extends IntegrationTest {

    @Autowired
    private SubjectRepository subjects;

    private final UUID userId = UUID.randomUUID();
    private UUID java;

    @BeforeEach
    void materias() {
        subjects.saveAndFlush(new Subject(userId, "Inglês", 1));
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

    private String semana(UUID usuario) throws Exception {
        return mockMvc.perform(get("/api/study/calendar").param("from", "2026-09-28").param("to", "2026-10-04")
                        .with(usuario(usuario)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // E12 T2 (CA1, CA2)
    @Test
    void semanaMostraOEstudadoAsRevisoesAtrasadasEAsAulasQueFaltam() throws Exception {
        // Segunda 28/09: duas aulas de Java (meta 2, cumprida); revisões vencem em 29/09
        estudarAula(java, Instant.parse("2026-09-28T12:00:00Z"), "Streams");
        estudarAula(java, Instant.parse("2026-09-28T14:00:00Z"), "Lambdas");

        // Quinta 01/10
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));
        String body = semana(userId);

        assertThat(JsonPath.<List<String>>read(body, "$[*].date")).containsExactly(
                "2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04");
        assertThat(JsonPath.<List<String>>read(body, "$[0].items[*].title")).containsExactly("Streams", "Lambdas");
        assertThat(JsonPath.<List<String>>read(body, "$[0].items[*].kind")).containsOnly("DONE");
        assertThat(JsonPath.<List<Integer>>read(body, "$[0].items[*].minutes")).containsOnly(50);
        // Vencidas em 29/09 aparecem hoje como atrasadas, não no dia 29
        assertThat(JsonPath.<List<Object>>read(body, "$[1].items")).isEmpty();
        assertThat(JsonPath.<List<String>>read(body, "$[3].items[?(@.kind == 'REVIEW')].title")).containsExactly("Streams", "Lambdas");
        assertThat(JsonPath.<List<Boolean>>read(body, "$[3].items[?(@.kind == 'REVIEW')].overdue")).containsOnly(true);
        // Java cumpriu a meta; Inglês (meta 2) aparece duas vezes, de hoje a domingo
        assertThat(JsonPath.<List<String>>read(body, "$[*].items[?(@.kind == 'LESSON')].subjectName")).containsExactly("Inglês", "Inglês");
        assertThat(JsonPath.<List<Object>>read(body, "$[0:3].items[?(@.kind == 'LESSON')]")).isEmpty();
    }

    @Test
    void intervaloInvalidoOuGrandeDemaisE400() throws Exception {
        mockMvc.perform(get("/api/study/calendar").param("from", "2026-10-04").param("to", "2026-09-28").with(usuario(userId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/study/calendar").param("from", "2026-01-01").param("to", "2026-06-30").with(usuario(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void naoMostraOQueEDeOutroUsuario() throws Exception {
        estudarAula(java, Instant.parse("2026-09-28T12:00:00Z"), "Streams");
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));

        assertThat(JsonPath.<List<Object>>read(semana(UUID.randomUUID()), "$[*].items[*]")).isEmpty();
    }
}
