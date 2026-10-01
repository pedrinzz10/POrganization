package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Dias de estudo por matéria e arrastar a aula na agenda (E13). Hoje: quinta 01/10/2026. */
class StudyCalendarMoveIT extends IntegrationTest {

    @Autowired
    private SubjectRepository subjects;

    private final UUID userId = UUID.randomUUID();
    private UUID ingles;

    @BeforeEach
    void materia() {
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));
        // Inglês: meta padrão de 2 aulas por semana
        ingles = subjects.saveAndFlush(new Subject(userId, "Inglês", 1)).getId();
    }

    @SuppressWarnings("unchecked")
    private List<String> diasComAula() throws Exception {
        String body = mockMvc.perform(get("/api/study/calendar").param("from", "2026-09-28").param("to", "2026-10-04")
                        .with(usuario(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> dias = JsonPath.read(body, "$");
        return dias.stream()
                .filter(d -> ((List<Map<String, Object>>) d.get("items")).stream().anyMatch(i -> "LESSON".equals(i.get("kind"))))
                .map(d -> (String) d.get("date"))
                .toList();
    }

    private ResultActions mover(String de, String para) throws Exception {
        return mockMvc.perform(post("/api/study/calendar/moves").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectId\":\"%s\",\"from\":\"%s\",\"to\":\"%s\"}".formatted(ingles, de, para)));
    }

    // E13 T3 (CA1)
    @Test
    void diasDeEstudoDaMateriaLimitamADistribuicao() throws Exception {
        mockMvc.perform(put("/api/subjects/" + ingles).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Inglês\",\"studyDays\":[\"FRI\",\"SUN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studyDays[0]").value("FRI"))
                .andExpect(jsonPath("$.studyDays[1]").value("SUN"));

        assertThat(diasComAula()).containsExactly("2026-10-02", "2026-10-04");

        // Lista vazia volta a "qualquer dia"
        mockMvc.perform(put("/api/subjects/" + ingles).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Inglês\",\"studyDays\":[]}"))
                .andExpect(jsonPath("$.studyDays").isEmpty());
    }

    // E13 T4 (CA2, CA3)
    @Test
    void arrastarFixaAAulaNoDiaNovoEAOutraFicaOndeEstava() throws Exception {
        List<String> antes = diasComAula();
        assertThat(antes).hasSize(2);
        String origem = antes.get(0);
        String outra = antes.get(1);
        String destino = List.of("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04").stream()
                .filter(d -> !antes.contains(d)).findFirst().orElseThrow();

        mover(origem, destino).andExpect(status().isOk());

        assertThat(diasComAula()).containsExactlyInAnyOrder(destino, outra);

        // Para o passado, para outra semana ou para um dia que já tem aula dela: recusado
        mover(destino, "2026-09-30").andExpect(status().isBadRequest());
        mover(destino, "2026-10-06").andExpect(status().isBadRequest());
        mover(destino, outra).andExpect(status().isConflict());
        // De um dia que não tem aula dela: a agenda estava desatualizada
        String semAula = List.of("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04").stream()
                .filter(d -> !d.equals(destino) && !d.equals(outra)).findFirst().orElseThrow();
        mover(semAula, "2026-10-04".equals(semAula) ? "2026-10-03" : "2026-10-04").andExpect(status().isConflict());

        // Voltar ao automático devolve a distribuição original
        mockMvc.perform(delete("/api/study/calendar/pins").param("subjectId", ingles.toString()).param("week", "2026-10-01")
                        .with(usuario(userId)))
                .andExpect(status().isNoContent());
        assertThat(diasComAula()).containsExactlyElementsOf(antes);
    }

    @Test
    void naoMoveMateriaDeOutroUsuario() throws Exception {
        mockMvc.perform(post("/api/study/calendar/moves").with(usuario(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"%s\",\"from\":\"2026-10-01\",\"to\":\"2026-10-02\"}".formatted(ingles)))
                .andExpect(status().isNotFound());
    }
}
