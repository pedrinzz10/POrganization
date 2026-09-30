package com.porganization.finance.categories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CategoryControllerIT extends IntegrationTest {

    private static final String BASE = "/api/finance/categories";

    private final UUID userId = UUID.randomUUID();

    // F02 T1 (CA1)
    @Test
    void primeiroAcessoCriaAsCategoriasPadraoUmaUnicaVez() throws Exception {
        String primeira = mockMvc.perform(get(BASE).with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("Alimentação", "Transporte", "Moradia", "Lazer",
                        "Saúde", "Educação", "Salário", "Outros")))
                .andReturn().getResponse().getContentAsString();
        String segunda = mockMvc.perform(get(BASE).with(usuario(userId))).andReturn().getResponse().getContentAsString();

        assertThat(segunda).isEqualTo(primeira);
        assertThat(jdbc.queryForObject("select count(*) from categories where user_id = ?", Integer.class, userId)).isEqualTo(8);
        List<String> kinds = JsonPath.read(primeira, "$[?(@.name == 'Salário')].kind");
        assertThat(kinds).containsExactly("INCOME");
    }

    @Test
    void apagarTodasNaoFazOSeedVoltar() throws Exception {
        String body = mockMvc.perform(get(BASE).with(usuario(userId))).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[*].id");
        for (String id : ids) {
            mockMvc.perform(delete(BASE + "/" + id).with(usuario(userId))).andExpect(status().isNoContent());
        }

        mockMvc.perform(get(BASE).with(usuario(userId))).andExpect(jsonPath("$", hasSize(0)));
    }

    // F02 T2 (CA2): categoria sem uso pode ser excluída; o caso "em uso → 409" entra na F03
    @Test
    void criarEExcluirCategoria() throws Exception {
        String body = mockMvc.perform(post(BASE).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pets\",\"kind\":\"EXPENSE\",\"color\":\"#FF9800\",\"icon\":\"pets\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("EXPENSE"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mockMvc.perform(delete(BASE + "/" + id).with(usuario(userId))).andExpect(status().isNoContent());
    }

    @Test
    void nomeRepetidoNoMesmoTipoResponde409() throws Exception {
        mockMvc.perform(get(BASE).with(usuario(userId)));
        mockMvc.perform(post(BASE).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"lazer\",\"kind\":\"EXPENSE\"}"))
                .andExpect(status().isConflict());
        // mesmo nome em outro tipo é permitido
        mockMvc.perform(post(BASE).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lazer\",\"kind\":\"INCOME\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void tagsFinanceiras() throws Exception {
        mockMvc.perform(post("/api/finance/tags").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"viagem\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/finance/tags").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Viagem\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/finance/tags").with(usuario(userId)))
                .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("viagem")));
    }

    @Test
    void categoriaDeOutroUsuarioResponde404() throws Exception {
        String body = mockMvc.perform(get(BASE).with(usuario(userId))).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$[0].id");
        mockMvc.perform(delete(BASE + "/" + id).with(usuario(UUID.randomUUID()))).andExpect(status().isNotFound());
    }
}
