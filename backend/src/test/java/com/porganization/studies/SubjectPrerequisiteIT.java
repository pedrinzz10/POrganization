package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** Pré-requisitos entre matérias (E17). Hoje: quinta 01/10/2026. */
class SubjectPrerequisiteIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();

    private ResultActions enviar(String metodo, String url, String json) throws Exception {
        var builder = metodo.equals("PUT") ? put(url) : post(url);
        return mockMvc.perform(builder.with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String materia(String json) throws Exception {
        return JsonPath.read(enviar("POST", "/api/subjects", json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String semana() throws Exception {
        return mockMvc.perform(get("/api/study/calendar").param("from", "2026-09-28").param("to", "2026-10-04").with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
    }

    private List<String> nomesNaSemana() throws Exception {
        return JsonPath.read(semana(), "$[*].items[?(@.kind == 'LESSON')].subjectName");
    }

    /** Estuda a próxima aula da lista (sessão de 50 min). */
    private void estudar(String materia) throws Exception {
        String sessao = JsonPath.read(enviar("POST", "/api/study/sessions", "{\"subjectId\":\"" + materia + "\",\"type\":\"LESSON\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        clock.setInstant(clock.instant().plusSeconds(50 * 60));
        enviar("POST", "/api/study/sessions/" + sessao + "/finish", "{}").andExpect(status().isOk());
    }

    @BeforeEach
    void hoje() {
        clock.setInstant(Instant.parse("2026-10-01T12:00:00Z"));
    }

    // E17 T1 (CA1, CA2)
    @Test
    void fisica2SoEntraDepoisDeTerminarFisica1MasDaParaIncluirAMao() throws Exception {
        String f1 = materia("{\"name\":\"Física I\",\"lessonMode\":\"PLANNED\"}");
        enviar("POST", "/api/subjects/" + f1 + "/planned-lessons", "{\"titles\":[\"Cinemática\",\"Dinâmica\"]}");
        String f2 = materia("{\"name\":\"Física II\",\"lessonMode\":\"PLANNED\",\"prerequisiteIds\":[\"" + f1 + "\"]}");

        mockMvc.perform(get("/api/subjects/" + f2).with(usuario(userId)))
                .andExpect(jsonPath("$.prerequisiteIds[0]").value(f1))
                .andExpect(jsonPath("$.blockedBy[0].name").value("Física I"))
                .andExpect(jsonPath("$.blockedBy[0].done").value(0))
                .andExpect(jsonPath("$.blockedBy[0].total").value(2));
        assertThat(nomesNaSemana()).contains("Física I").doesNotContain("Física II");
        mockMvc.perform(get("/api/study/today").with(usuario(userId)))
                .andExpect(jsonPath("$.lessons[*].subjectName").value(not(hasItem("Física II"))));
        String gerada = mockMvc.perform(post("/api/study/week/generate").param("week", "2026-10-01").with(usuario(userId)))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(gerada, "$[*].items[*].subjectName")).doesNotContain("Física II");

        // Exceção à mão: incluir no plano funciona
        enviar("POST", "/api/study/week/slots", "{\"subjectId\":\"" + f2 + "\",\"day\":\"2026-10-03\"}").andExpect(status().isOk());
        assertThat(nomesNaSemana()).contains("Física II");

        // Terminou Física I: Física II liberada e Física I concluída (sai das sugestões)
        estudar(f1);
        estudar(f1);
        mockMvc.perform(get("/api/subjects").with(usuario(userId)))
                .andExpect(jsonPath("$[0].completed").value(true))
                .andExpect(jsonPath("$[1].blockedBy").isEmpty());
        // Sem plano na semana, Hoje volta às sugestões automáticas
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/study/week")
                .param("week", "2026-10-01").with(usuario(userId)));
        mockMvc.perform(get("/api/study/today").with(usuario(userId)))
                .andExpect(jsonPath("$.lessons[*].subjectName").value(hasItem("Física II")))
                .andExpect(jsonPath("$.lessons[*].subjectName").value(not(hasItem("Física I"))));
    }

    // E17 T2 (CA3)
    @Test
    void materiaLivreLiberaQuandoMarcadaComoConcluida() throws Exception {
        String i1 = materia("{\"name\":\"Inglês I\"}");
        String i2 = materia("{\"name\":\"Inglês II\",\"prerequisiteIds\":[\"" + i1 + "\"]}");
        mockMvc.perform(get("/api/subjects/" + i2).with(usuario(userId))).andExpect(jsonPath("$.blockedBy[0].name").value("Inglês I"));

        enviar("PUT", "/api/subjects/" + i1, "{\"name\":\"Inglês I\",\"completed\":true}")
                .andExpect(jsonPath("$.completed").value(true));
        mockMvc.perform(get("/api/subjects/" + i2).with(usuario(userId))).andExpect(jsonPath("$.blockedBy").isEmpty());
        assertThat(nomesNaSemana()).contains("Inglês II").doesNotContain("Inglês I");

        enviar("PUT", "/api/subjects/" + i1, "{\"name\":\"Inglês I\",\"completed\":false}");
        mockMvc.perform(get("/api/subjects/" + i2).with(usuario(userId))).andExpect(jsonPath("$.blockedBy[0].name").value("Inglês I"));

        // Arquivar a matéria de que depende não bloqueia mais
        enviar("PUT", "/api/subjects/" + i1, "{\"name\":\"Inglês I\",\"archived\":true}");
        mockMvc.perform(get("/api/subjects/" + i2).with(usuario(userId))).andExpect(jsonPath("$.blockedBy").isEmpty());
    }

    // E17 T3 (CA4)
    @Test
    void recusaDependerDelaMesmaCicloEMateriaDeOutro() throws Exception {
        String a = materia("{\"name\":\"Matemática Básica\"}");
        String b = materia("{\"name\":\"Matemática\",\"prerequisiteIds\":[\"" + a + "\"]}");
        String c = materia("{\"name\":\"Física I\",\"prerequisiteIds\":[\"" + b + "\"]}");

        enviar("PUT", "/api/subjects/" + a, "{\"name\":\"Matemática Básica\",\"prerequisiteIds\":[\"" + a + "\"]}")
                .andExpect(status().isBadRequest());
        // a → c → b → a seria ciclo
        enviar("PUT", "/api/subjects/" + a, "{\"name\":\"Matemática Básica\",\"prerequisiteIds\":[\"" + c + "\"]}")
                .andExpect(status().isBadRequest());
        enviar("PUT", "/api/subjects/" + a, "{\"name\":\"Matemática Básica\",\"prerequisiteIds\":[\"" + UUID.randomUUID() + "\"]}")
                .andExpect(status().isBadRequest());
        // Lista vazia tira as dependências
        enviar("PUT", "/api/subjects/" + c, "{\"name\":\"Física I\",\"prerequisiteIds\":[]}")
                .andExpect(jsonPath("$.prerequisiteIds").isEmpty())
                .andExpect(jsonPath("$.blockedBy").isEmpty());
    }
}
