package com.porganization.studies;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PlannedLessonIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();

    private String materia(String json) throws Exception {
        String body = mockMvc.perform(post("/api/subjects").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String base(String materia) {
        return "/api/subjects/" + materia + "/planned-lessons";
    }

    private ResultActions incluir(String materia, String... titulos) throws Exception {
        String lista = String.join(",", java.util.Arrays.stream(titulos).map(t -> "\"" + t + "\"").toList());
        return mockMvc.perform(post(base(materia)).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"titles\":[" + lista + "]}"));
    }

    private List<String> ids(String materia) throws Exception {
        String body = mockMvc.perform(get(base(materia)).with(usuario(userId))).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[*].id");
    }

    // E14 T1 (CA1)
    @Test
    void materiaComAulasDefinidasGuardaOTipoEAContagem() throws Exception {
        String java = materia("{\"name\":\"Java\",\"lessonMode\":\"PLANNED\"}");
        String livre = materia("{\"name\":\"Inglês\"}");

        incluir(java, "Variáveis", " ", "Laços", "Classes").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains("Variáveis", "Laços", "Classes")))
                .andExpect(jsonPath("$[*].position").value(contains(1, 2, 3)))
                .andExpect(jsonPath("$[0].lessonId").doesNotExist());

        mockMvc.perform(get("/api/subjects").with(usuario(userId)))
                .andExpect(jsonPath("$[0].lessonMode").value("PLANNED"))
                .andExpect(jsonPath("$[0].plannedTotal").value(3))
                .andExpect(jsonPath("$[0].plannedDone").value(0))
                .andExpect(jsonPath("$[1].lessonMode").value("FREE"))
                .andExpect(jsonPath("$[1].plannedTotal").value(0));

        // Trocar o tipo pela edição
        mockMvc.perform(put("/api/subjects/" + livre).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Inglês\",\"lessonMode\":\"PLANNED\"}"))
                .andExpect(jsonPath("$.lessonMode").value("PLANNED"));
    }

    // E14 T2 (CA2)
    @Test
    void reordenarRenomearEExcluirMantemASequencia() throws Exception {
        String java = materia("{\"name\":\"Java\",\"lessonMode\":\"PLANNED\"}");
        incluir(java, "A", "B", "C");
        List<String> ids = ids(java);

        mockMvc.perform(put(base(java) + "/order").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"%s\",\"%s\",\"%s\"]}".formatted(ids.get(2), ids.get(0), ids.get(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title").value(contains("C", "A", "B")));
        mockMvc.perform(put(base(java) + "/order").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"%s\",\"%s\"]}".formatted(ids.get(0), ids.get(1))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch(base(java) + "/" + ids.get(0)).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" Introdução \"}"))
                .andExpect(jsonPath("$.title").value("Introdução"));

        mockMvc.perform(delete(base(java) + "/" + ids.get(2)).with(usuario(userId))).andExpect(status().isNoContent());
        mockMvc.perform(get(base(java)).with(usuario(userId)))
                .andExpect(jsonPath("$[*].title").value(contains("Introdução", "B")))
                .andExpect(jsonPath("$[*].position").value(contains(1, 2)));

        incluir(java, "D").andExpect(jsonPath("$[2].position").value(3));
    }

    // E14 T3 (CA3)
    @Test
    void materiaDeOutroUsuarioDa404EListaVaziaDa400() throws Exception {
        String java = materia("{\"name\":\"Java\",\"lessonMode\":\"PLANNED\"}");
        UUID outro = UUID.randomUUID();
        mockMvc.perform(get(base(java)).with(usuario(outro))).andExpect(status().isNotFound());
        mockMvc.perform(post(base(java)).with(usuario(outro)).contentType(MediaType.APPLICATION_JSON).content("{\"titles\":[\"X\"]}"))
                .andExpect(status().isNotFound());
        incluir(java, " ").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/subjects").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"lessonMode\":\"OUTRO\"}")).andExpect(status().isBadRequest());
    }
}
