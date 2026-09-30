package com.porganization.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porganization.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

class GlobalExceptionHandlerTest extends IntegrationTest {

    private static final String BASE = ErrosDeTesteController.BASE;

    private final JwtRequestPostProcessor usuario = jwt().jwt(j -> j.subject(UUID.randomUUID().toString()));

    // B05 T1 (CA1)
    @Test
    void bodyInvalidoResponde400ComOsCampos() throws Exception {
        mockMvc.perform(post(BASE + "/validacao").with(usuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\": \"\", \"quantidade\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("titulo")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("quantidade")))
                .andExpect(jsonPath("$.errors[?(@.field == 'titulo')].message").value(hasItem("não deve estar em branco")));
    }

    // B05 T1 (CA1): JSON quebrado também é 400 no mesmo formato
    @Test
    void jsonMalformadoResponde400() throws Exception {
        mockMvc.perform(post(BASE + "/validacao").with(usuario)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requisição inválida"));
    }

    // B05 T2 (CA2)
    @Test
    void notFoundExceptionResponde404() throws Exception {
        mockMvc.perform(get(BASE + "/nao-encontrado").with(usuario))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Não encontrado"))
                .andExpect(jsonPath("$.detail").value("Compromisso não encontrado"));
    }

    // B05 T3 (CA3)
    @Test
    void erroInesperadoResponde500SemVazarDetalhes() throws Exception {
        mockMvc.perform(get(BASE + "/inesperado").with(usuario))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(content().string(not(containsString("segredo"))))
                .andExpect(content().string(not(containsString("RuntimeException"))))
                .andExpect(content().string(not(containsString("at com.porganization"))));
    }
}
