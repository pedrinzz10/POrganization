package com.porganization.studies;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SubjectControllerIT extends IntegrationTest {

    private final UUID userId = UUID.randomUUID();

    private String criarMateria(UUID dono, String json) throws Exception {
        String body = mockMvc.perform(post("/api/subjects").with(usuario(dono))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String criarTag(String nome) throws Exception {
        String body = mockMvc.perform(post("/api/tags").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + nome + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions ordenar(String... ids) throws Exception {
        return mockMvc.perform(put("/api/subjects/order").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[" + String.join(",", java.util.Arrays.stream(ids).map(i -> "\"" + i + "\"").toList()) + "]}"));
    }

    // E02 T1 (CA1)
    @Test
    void novaMateriaEntraNoFimDaPrioridade() throws Exception {
        criarMateria(userId, "{\"name\":\"Inglês\"}");
        criarMateria(userId, "{\"name\":\"Java\"}");

        mockMvc.perform(post("/api/subjects").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cálculo\",\"sessionsPerWeek\":3,\"lessonMinutes\":40}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priorityOrder").value(3))
                .andExpect(jsonPath("$.sessionsPerWeek").value(3))
                .andExpect(jsonPath("$.lessonMinutes").value(40));
    }

    // E02 T2 (CA2)
    @Test
    void reordenarGravaAOrdemERecusaListaInvalida() throws Exception {
        String a = criarMateria(userId, "{\"name\":\"A\"}");
        String b = criarMateria(userId, "{\"name\":\"B\"}");
        String c = criarMateria(userId, "{\"name\":\"C\"}");

        ordenar(c, a, b).andExpect(status().isOk());
        mockMvc.perform(get("/api/subjects").with(usuario(userId)))
                .andExpect(jsonPath("$[*].name").value(contains("C", "A", "B")))
                .andExpect(jsonPath("$[*].priorityOrder").value(contains(1, 2, 3)));

        ordenar(a, a, b).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("ids"));
        ordenar(a, b).andExpect(status().isBadRequest());
        String deOutro = criarMateria(UUID.randomUUID(), "{\"name\":\"De outra pessoa\"}");
        ordenar(c, a, deOutro).andExpect(status().isBadRequest());

        // a ordem gravada continua a mesma depois das tentativas inválidas
        mockMvc.perform(get("/api/subjects").with(usuario(userId)))
                .andExpect(jsonPath("$[*].name").value(contains("C", "A", "B")));
    }

    // E02 T3 (CA3)
    @Test
    void filtraPorTag() throws Exception {
        String linguas = criarTag("línguas");
        String faculdade = criarTag("faculdade");
        criarMateria(userId, "{\"name\":\"Inglês\",\"tagIds\":[\"" + linguas + "\"]}");
        criarMateria(userId, "{\"name\":\"Java\",\"tagIds\":[\"" + faculdade + "\"]}");

        mockMvc.perform(get("/api/subjects").param("tag", "Línguas").with(usuario(userId)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Inglês"))
                .andExpect(jsonPath("$[0].tags[0].name").value("línguas"));
    }

    @Test
    void tagRepetidaResponde409() throws Exception {
        criarTag("faculdade");
        mockMvc.perform(post("/api/tags").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Faculdade\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"));
    }

    @Test
    void tagDeOutroUsuarioNaMateriaResponde400() throws Exception {
        String body = mockMvc.perform(post("/api/tags").with(usuario(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"alheia\"}"))
                .andReturn().getResponse().getContentAsString();
        String alheia = JsonPath.read(body, "$.id");

        mockMvc.perform(post("/api/subjects").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java\",\"tagIds\":[\"" + alheia + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("tagIds"));
    }

    @Test
    void materiaDeOutroUsuarioResponde404() throws Exception {
        String id = criarMateria(UUID.randomUUID(), "{\"name\":\"Alheia\"}");
        mockMvc.perform(get("/api/subjects/" + id).with(usuario(userId))).andExpect(status().isNotFound());
    }

    @Test
    void arquivadaSaiDaListaPadraoEDaOrdem() throws Exception {
        String a = criarMateria(userId, "{\"name\":\"A\"}");
        String b = criarMateria(userId, "{\"name\":\"B\"}");
        mockMvc.perform(put("/api/subjects/" + a).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"archived\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/subjects").with(usuario(userId))).andExpect(jsonPath("$[*].name").value(contains("B")));
        ordenar(b).andExpect(status().isOk());
        mockMvc.perform(get("/api/subjects").param("includeArchived", "true").with(usuario(userId)))
                .andExpect(jsonPath("$[*].name").value(contains("B", "A")));
    }

    @Test
    void validaOsLimites() throws Exception {
        mockMvc.perform(post("/api/subjects").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"sessionsPerWeek\":22}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sessionsPerWeek"));
    }
}
