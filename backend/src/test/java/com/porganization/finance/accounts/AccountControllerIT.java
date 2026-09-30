package com.porganization.finance.accounts;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AccountControllerIT extends IntegrationTest {

    private static final String BASE = "/api/finance/accounts";

    private final UUID userId = UUID.randomUUID();

    private String criar(String json) throws Exception {
        String body = mockMvc.perform(post(BASE).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    // F01 T1 (CA1)
    @Test
    void valoresTrafegamComoStringDecimalComDuasCasas() throws Exception {
        String id = criar("{\"name\":\"Nubank\",\"type\":\"CHECKING\",\"initialBalance\":\"1000.10\"}");

        mockMvc.perform(get(BASE + "/" + id).with(usuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialBalance").value("1000.10"))
                .andExpect(jsonPath("$.balance").value("1000.10"))
                // no JSON cru é uma string, não o número 1000.1
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"initialBalance\":\"1000.10\"")));

        BigDecimal guardado = jdbc.queryForObject("select initial_balance from accounts where id = ?::uuid", BigDecimal.class, id);
        org.assertj.core.api.Assertions.assertThat(guardado).isEqualByComparingTo("1000.10");
        org.assertj.core.api.Assertions.assertThat(guardado.scale()).isEqualTo(2);
    }

    // F01 T1 (CA1): entrada com menos casas é normalizada para 2
    @Test
    void entradaComUmaCasaViraDuas() throws Exception {
        String id = criar("{\"name\":\"Carteira\",\"type\":\"CASH\",\"initialBalance\":\"50.5\"}");
        mockMvc.perform(get(BASE + "/" + id).with(usuario(userId))).andExpect(jsonPath("$.initialBalance").value("50.50"));
    }

    @Test
    void maisDeDuasCasasResponde400() throws Exception {
        mockMvc.perform(post(BASE).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"type\":\"CASH\",\"initialBalance\":\"10.123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("initialBalance"));
    }

    // F01 T2 (CA2): arquivar sai da lista padrão; excluir conta sem uso funciona
    @Test
    void arquivarEExcluir() throws Exception {
        String poupanca = criar("{\"name\":\"Poupança\",\"type\":\"SAVINGS\",\"initialBalance\":\"0.00\"}");
        String corrente = criar("{\"name\":\"Corrente\",\"type\":\"CHECKING\",\"initialBalance\":\"10.00\"}");

        mockMvc.perform(patch(BASE + "/" + poupanca).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"archived\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        mockMvc.perform(get(BASE).with(usuario(userId))).andExpect(jsonPath("$[*].name").value(contains("Corrente")));
        mockMvc.perform(get(BASE).param("includeArchived", "true").with(usuario(userId)))
                .andExpect(jsonPath("$[*].name").value(contains("Corrente", "Poupança")));

        mockMvc.perform(delete(BASE + "/" + corrente).with(usuario(userId))).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + corrente).with(usuario(userId))).andExpect(status().isNotFound());
    }

    @Test
    void contaDeOutroUsuarioResponde404() throws Exception {
        String id = criar("{\"name\":\"Minha\",\"type\":\"CASH\",\"initialBalance\":\"1.00\"}");
        mockMvc.perform(get(BASE + "/" + id).with(usuario(UUID.randomUUID()))).andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/" + id).with(usuario(UUID.randomUUID()))).andExpect(status().isNotFound());
    }
}
