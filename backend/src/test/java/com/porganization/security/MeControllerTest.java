package com.porganization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porganization.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeControllerTest extends IntegrationTest {

    // B04 T2 (CA2)
    @Test
    void devolveOUserIdDoSub() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    // B04 T5 (CA5)
    @Test
    void primeiroAcessoCriaUserSettingsSemDuplicar() throws Exception {
        UUID userId = UUID.randomUUID();
        var token = jwt().jwt(j -> j.subject(userId.toString()).claim("email", "pedro@teste.com"));

        mockMvc.perform(get("/api/me").with(token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pedro@teste.com"))
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"));

        Map<String, Object> linha = jdbc.queryForMap(
                "select count(*) as total, max(email) as email, max(timezone) as timezone"
                        + " from user_settings where user_id = ?", userId);
        assertThat(linha).containsEntry("total", 1L)
                .containsEntry("email", "pedro@teste.com")
                .containsEntry("timezone", "America/Sao_Paulo");
    }

    // B04 T5 (CA5): o e-mail acompanha o token se o usuário trocar de e-mail no Supabase
    @Test
    void atualizaEmailQuandoOTokenMuda() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "antigo@teste.com"))));
        mockMvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject(userId.toString()).claim("email", "novo@teste.com"))))
                .andExpect(jsonPath("$.email").value("novo@teste.com"));
    }

    // CurrentUser: sub que não é UUID não é um usuário do Supabase
    @Test
    void subQueNaoEhUuidResponde401() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject("nao-e-uuid"))))
                .andExpect(status().isUnauthorized());
    }
}
