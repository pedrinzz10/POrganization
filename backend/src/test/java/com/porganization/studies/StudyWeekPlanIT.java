package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Plano da semana (E15). Hoje: quinta 01/10/2026; semana de 28/09 a 04/10. */
class StudyWeekPlanIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();
    private String java;
    private String ingles;

    private String materia(String json) throws Exception {
        String body = mockMvc.perform(post("/api/subjects").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @BeforeEach
    void materias() throws Exception {
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));
        java = materia("{\"name\":\"Java\",\"sessionsPerWeek\":2,\"lessonMode\":\"PLANNED\"}");
        ingles = materia("{\"name\":\"Inglês\",\"sessionsPerWeek\":1}");
        mockMvc.perform(post("/api/subjects/" + java + "/planned-lessons").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titles\":[\"Variáveis\",\"Laços\",\"Classes\"]}")).andExpect(status().isOk());
    }

    /** Dias com aula da matéria na resposta (de uma ação ou do GET). */
    @SuppressWarnings("unchecked")
    private static List<String> diasDe(String body, String nome) {
        List<Map<String, Object>> dias = JsonPath.read(body, "$");
        return dias.stream()
                .filter(d -> ((List<Map<String, Object>>) d.get("items")).stream()
                        .anyMatch(i -> "LESSON".equals(i.get("kind")) && nome.equals(i.get("subjectName"))))
                .map(d -> (String) d.get("date"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> aulasDe(String body, String nome) {
        List<Map<String, Object>> dias = JsonPath.read(body, "$");
        return dias.stream()
                .flatMap(d -> ((List<Map<String, Object>>) d.get("items")).stream())
                .filter(i -> "LESSON".equals(i.get("kind")) && nome.equals(i.get("subjectName")))
                .toList();
    }

    private String semana() throws Exception {
        return mockMvc.perform(get("/api/study/calendar").param("from", "2026-09-28").param("to", "2026-10-04").with(usuario(userId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions incluir(String materia, String dia) throws Exception {
        return mockMvc.perform(post("/api/study/week/slots").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectId\":\"%s\",\"day\":\"%s\"}".formatted(materia, dia)));
    }

    private ResultActions tirar(String materia, String dia) throws Exception {
        return mockMvc.perform(delete("/api/study/week/slots").param("subjectId", materia).param("day", dia).with(usuario(userId)));
    }

    // E15 T3 (CA1)
    @Test
    void gerarSemanaSorteiaOsDiasDaMetaEMarcaASemana() throws Exception {
        String body = mockMvc.perform(post("/api/study/week/generate").param("week", "2026-10-03").with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].planned").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(diasDe(body, "Java")).hasSize(2).allSatisfy(d -> assertThat(d).isGreaterThanOrEqualTo("2026-10-01"));
        assertThat(diasDe(body, "Inglês")).hasSize(1);
        assertThat(diasDe(semana(), "Java")).isEqualTo(diasDe(body, "Java"));

        // Semana passada não dá para gerar
        mockMvc.perform(post("/api/study/week/generate").param("week", "2026-09-27").with(usuario(userId)))
                .andExpect(status().isBadRequest());
    }

    // E15 T4 (CA2)
    @Test
    void ajustarSemanaAutomaticaGuardaAPrevisaoELimparVoltaAoAutomatico() throws Exception {
        List<String> antes = diasDe(semana(), "Inglês");
        assertThat(antes).hasSize(1);
        String livre = List.of("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04").stream()
                .filter(d -> !antes.contains(d)).findFirst().orElseThrow();

        // Incluir Inglês num dia: a aula prevista continua (a previsão virou plano) e entra mais uma
        String body = incluir(ingles, livre).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].planned").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(diasDe(body, "Inglês")).containsExactlyInAnyOrder(antes.get(0), livre);
        assertThat(diasDe(body, "Java")).hasSize(2);

        incluir(ingles, livre).andExpect(status().isConflict());
        incluir(ingles, "2026-09-30").andExpect(status().isBadRequest());

        body = tirar(ingles, antes.get(0)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(diasDe(body, "Inglês")).containsExactly(livre);
        tirar(ingles, antes.get(0)).andExpect(status().isConflict());

        mockMvc.perform(delete("/api/study/week").param("week", "2026-10-01").with(usuario(userId))).andExpect(status().isNoContent());
        String automatica = semana();
        assertThat(diasDe(automatica, "Inglês")).isEqualTo(antes);
        assertThat(JsonPath.<Boolean>read(automatica, "$[0].planned")).isFalse();
    }

    // E15 T5 (CA3)
    @Test
    void aulasDeJavaRecebemAsAulasDefinidasNaOrdemDosDias() throws Exception {
        String body = semana();
        List<Map<String, Object>> aulas = aulasDe(body, "Java");
        assertThat(aulas).hasSize(2);
        assertThat(aulas).extracting(a -> a.get("title")).containsExactly("Variáveis", "Laços");
        assertThat(aulas).allSatisfy(a -> assertThat(a.get("plannedLessonId")).isNotNull());

        // Inglês é livre: a aula sugerida não tem nome
        assertThat(aulasDe(body, "Inglês")).allSatisfy(a -> assertThat(a.get("title")).isNull());
    }

    @Test
    void materiaDeOutroUsuario404() throws Exception {
        mockMvc.perform(post("/api/study/week/slots").with(usuario(UUID.randomUUID())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"%s\",\"day\":\"2026-10-02\"}".formatted(java)))
                .andExpect(status().isNotFound());
    }
}
