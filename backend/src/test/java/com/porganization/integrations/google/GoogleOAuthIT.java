package com.porganization.integrations.google;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

class GoogleOAuthIT extends GoogleIT {

    private final UUID userId = UUID.randomUUID();

    private static String idToken(String email) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"email\":\"" + email + "\",\"sub\":\"123\"}").getBytes(StandardCharsets.UTF_8));
        return "eyJhbGciOiJSUzI1NiJ9." + payload + ".assinatura";
    }

    private void googleDevolveTokens() {
        google.stubFor(post("/token").withRequestBody(containing("grant_type=authorization_code"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""
                        {"access_token":"access-123","expires_in":3599,"refresh_token":"refresh-secreto",
                         "scope":"https://www.googleapis.com/auth/calendar.events openid email","token_type":"Bearer",
                         "id_token":"%s"}
                        """.formatted(idToken("pedro@gmail.com")))));
    }

    /** O state que a API pôs na URL do Google para este usuário. */
    private String stateDoAuthUrl() throws Exception {
        String body = mockMvc.perform(get("/api/integrations/google/auth-url").with(usuario(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.url");
        assertThat(url).startsWith(google.baseUrl() + "/o/oauth2/v2/auth?")
                .contains("client_id=client-id-de-teste", "access_type=offline", "prompt=consent", "calendar.events");
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
    }

    private int conexoes() {
        return jdbc.queryForObject("select count(*) from google_connections where user_id = ?", Integer.class, userId);
    }

    @Test
    void fluxoCompletoGuardaORefreshTokenCifradoENuncaDevolve() throws Exception {
        googleDevolveTokens();
        String state = stateDoAuthUrl();

        mockMvc.perform(get("/api/integrations/google/callback").param("code", "code-do-google").param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://app.porganization.test/configuracoes?google=ok"));

        google.verify(postRequestedFor(urlEqualTo("/token"))
                .withRequestBody(containing("code=code-do-google"))
                .withRequestBody(containing("client_secret=client-secret-de-teste")));
        // I06 CA2: nunca em texto puro
        String guardado = jdbc.queryForObject("select refresh_token_enc from google_connections where user_id = ?", String.class, userId);
        assertThat(guardado).isNotBlank().doesNotContain("refresh-secreto");

        String status = mockMvc.perform(get("/api/integrations/google").with(usuario(userId)))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.email").value("pedro@gmail.com"))
                .andExpect(jsonPath("$.calendarId").value("primary"))
                .andReturn().getResponse().getContentAsString();
        // I06 CA2: nunca em resposta da API
        assertThat(status).doesNotContain("refresh").doesNotContain("access-123").doesNotContain(guardado);
    }

    // I06 T1 (CA1)
    @Test
    void callbackComStateAdulteradoResponde400ENaoSalvaConexao() throws Exception {
        googleDevolveTokens();
        String state = stateDoAuthUrl();
        String outroUsuario = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (UUID.randomUUID() + ".9999999999.nonce").getBytes(StandardCharsets.UTF_8));
        String adulterado = outroUsuario + state.substring(state.lastIndexOf('.'));

        mockMvc.perform(get("/api/integrations/google/callback").param("code", "code").param("state", adulterado))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/integrations/google/callback").param("code", "code").param("state", "lixo"))
                .andExpect(status().isBadRequest());

        google.verify(0, postRequestedFor(urlEqualTo("/token")));
        assertThat(conexoes()).isZero();
    }

    @Test
    void stateExpiradoResponde400() throws Exception {
        String state = stateDoAuthUrl();
        clock.setInstant(clock.instant().plusSeconds(11 * 60));

        mockMvc.perform(get("/api/integrations/google/callback").param("code", "code").param("state", state))
                .andExpect(status().isBadRequest());
        assertThat(conexoes()).isZero();
    }

    @Test
    void usuarioRecusouNoGoogleVoltaComErro() throws Exception {
        String state = stateDoAuthUrl();

        mockMvc.perform(get("/api/integrations/google/callback").param("error", "access_denied").param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://app.porganization.test/configuracoes?google=erro"));
        assertThat(conexoes()).isZero();
    }

    // I06 T3 (CA3)
    @Test
    void desconectarRevogaNoGoogleEApagaAConexao() throws Exception {
        googleDevolveTokens();
        google.stubFor(post("/revoke").willReturn(aResponse().withStatus(200)));
        mockMvc.perform(get("/api/integrations/google/callback").param("code", "code").param("state", stateDoAuthUrl()));
        assertThat(conexoes()).isEqualTo(1);

        mockMvc.perform(delete("/api/integrations/google").with(usuario(userId))).andExpect(status().isNoContent());

        google.verify(postRequestedFor(urlEqualTo("/revoke")).withRequestBody(containing("token=refresh-secreto")));
        assertThat(conexoes()).isZero();
        mockMvc.perform(get("/api/integrations/google").with(usuario(userId))).andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void revogacaoRecusadaPeloGoogleApagaMesmoAssim() throws Exception {
        googleDevolveTokens();
        google.stubFor(post("/revoke").willReturn(aResponse().withStatus(400).withBody("{\"error\":\"invalid_token\"}")));
        mockMvc.perform(get("/api/integrations/google/callback").param("code", "code").param("state", stateDoAuthUrl()));

        mockMvc.perform(delete("/api/integrations/google").with(usuario(userId))).andExpect(status().isNoContent());

        assertThat(conexoes()).isZero();
    }
}
