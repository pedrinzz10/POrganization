package com.porganization.commitments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.porganization.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class CommitmentControllerIT extends IntegrationTest {

    private final UUID usuarioA = UUID.randomUUID();
    private final UUID usuarioB = UUID.randomUUID();

    private MvcResult criar(UUID userId, String json) throws Exception {
        return mockMvc.perform(post("/api/commitments").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
    }

    private String criarDentista(UUID userId) throws Exception {
        MvcResult result = criar(userId, "{\"title\":\"Dentista\",\"date\":\"2026-10-02\",\"startTime\":\"14:00\"}");
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // C02 T1 (CA1)
    @Test
    void criacaoRapidaResponde201ComLocation() throws Exception {
        MvcResult result = criar(usuarioA, "{\"title\":\"Dentista\",\"date\":\"2026-10-02\",\"startTime\":\"14:00\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).matches(".*/api/commitments/[0-9a-f-]{36}$");

        mockMvc.perform(get(location).with(usuario(usuarioA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dentista"))
                .andExpect(jsonPath("$.date").value("2026-10-02"))
                .andExpect(jsonPath("$.startTime").value("14:00"))
                .andExpect(jsonPath("$.allDay").value(false))
                .andExpect(jsonPath("$.done").value(false));
    }

    // C02 T2 (CA2)
    @Test
    void semTituloResponde400ApontandoOCampo() throws Exception {
        mockMvc.perform(post("/api/commitments").with(usuario(usuarioA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-10-02\",\"startTime\":\"14:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    // C02 T3 (CA3)
    @Test
    void outroUsuarioRecebe404EmGetPutEDelete() throws Exception {
        String id = criarDentista(usuarioA);
        String url = "/api/commitments/" + id;

        mockMvc.perform(get(url).with(usuario(usuarioB))).andExpect(status().isNotFound());
        mockMvc.perform(put(url).with(usuario(usuarioB)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Invadido\",\"date\":\"2026-10-02\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(url).with(usuario(usuarioB))).andExpect(status().isNotFound());

        // o registro continua intacto para o dono
        mockMvc.perform(get(url).with(usuario(usuarioA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dentista"));
    }

    @Test
    void putAtualizaEDeleteRemove() throws Exception {
        String url = "/api/commitments/" + criarDentista(usuarioA);

        mockMvc.perform(put(url).with(usuario(usuarioA)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Dentista (retorno)","date":"2026-10-09","startTime":"15:30","endTime":"16:15",
                                 "location":"Consultório","description":"Levar exames"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dentista (retorno)"))
                .andExpect(jsonPath("$.date").value("2026-10-09"))
                .andExpect(jsonPath("$.endTime").value("16:15"))
                .andExpect(jsonPath("$.location").value("Consultório"));

        mockMvc.perform(delete(url).with(usuario(usuarioA))).andExpect(status().isNoContent());
        mockMvc.perform(get(url).with(usuario(usuarioA))).andExpect(status().isNotFound());
    }

    @Test
    void semHorarioViraDiaTodo() throws Exception {
        MvcResult result = criar(usuarioA, "{\"title\":\"Aniversário da mãe\",\"date\":\"2026-10-20\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat((Boolean) JsonPath.read(result.getResponse().getContentAsString(), "$.allDay")).isTrue();
    }

    @Test
    void fimAntesDoInicioResponde400() throws Exception {
        mockMvc.perform(post("/api/commitments").with(usuario(usuarioA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Reunião\",\"date\":\"2026-10-02\",\"startTime\":\"15:00\",\"endTime\":\"14:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endTime"));
    }

    @Test
    void idQueNaoEhUuidResponde400() throws Exception {
        mockMvc.perform(get("/api/commitments/abc").with(usuario(usuarioA)))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(HttpHeaders.CONTENT_TYPE));
    }

    // ---------- lembretes (I04) ----------

    private void configurar(UUID userId, String json) throws Exception {
        mockMvc.perform(put("/api/settings").with(usuario(userId)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    // I04 T1 (CA1)
    @Test
    void criacaoRapidaGanhaOLembretePadraoDasConfiguracoes() throws Exception {
        configurar(usuarioA, "{\"timezone\":\"America/Sao_Paulo\",\"channels\":[\"PUSH\"],\"defaultReminderMinutes\":30}");

        String id = criarDentista(usuarioA);

        mockMvc.perform(get("/api/commitments/" + id).with(usuario(usuarioA)))
                .andExpect(jsonPath("$.reminders.length()").value(1))
                .andExpect(jsonPath("$.reminders[0].minutesBefore").value(30))
                .andExpect(jsonPath("$.reminders[0].channels").value(org.hamcrest.Matchers.contains("PUSH")));
        assertThat(jdbc.queryForObject("select count(*) from reminders where commitment_id = ?::uuid and minutes_before = 30 "
                + "and channels = '[\"PUSH\"]'::jsonb", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void semLembretePadraoOCompromissoNasceSemLembrete() throws Exception {
        String id = criarDentista(usuarioA);

        mockMvc.perform(get("/api/commitments/" + id).with(usuario(usuarioA)))
                .andExpect(jsonPath("$.reminders.length()").value(0));
    }

    // I04 CA2
    @Test
    void editarComListaVaziaApagaOsLembretesESemOCampoMantem() throws Exception {
        MvcResult criado = criar(usuarioA, """
                {"title":"Prova","date":"2026-10-02","startTime":"08:00",
                 "reminders":[{"minutesBefore":1440,"channels":["EMAIL"]},{"minutesBefore":60,"channels":["PUSH","EMAIL"]}]}
                """);
        String id = JsonPath.read(criado.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/commitments/" + id).with(usuario(usuarioA)))
                .andExpect(jsonPath("$.reminders[*].minutesBefore").value(org.hamcrest.Matchers.contains(60, 1440)));

        // Sem "reminders": mantém
        mockMvc.perform(put("/api/commitments/" + id).with(usuario(usuarioA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Prova de Java\",\"date\":\"2026-10-02\",\"startTime\":\"08:00\"}"))
                .andExpect(jsonPath("$.reminders.length()").value(2));

        // "reminders": [] apaga
        mockMvc.perform(put("/api/commitments/" + id).with(usuario(usuarioA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Prova de Java\",\"date\":\"2026-10-02\",\"startTime\":\"08:00\",\"reminders\":[]}"))
                .andExpect(jsonPath("$.reminders.length()").value(0));
        assertThat(jdbc.queryForObject("select count(*) from reminders where commitment_id = ?::uuid", Integer.class, id)).isZero();
    }

    @Test
    void lembreteSemCanalOuComAntecedenciaNegativaResponde400() throws Exception {
        MvcResult semCanal = criar(usuarioA, """
                {"title":"X","date":"2026-10-02","reminders":[{"minutesBefore":10,"channels":[]}]}
                """);
        assertThat(semCanal.getResponse().getStatus()).isEqualTo(400);
        MvcResult negativo = criar(usuarioA, """
                {"title":"X","date":"2026-10-02","reminders":[{"minutesBefore":-5,"channels":["PUSH"]}]}
                """);
        assertThat(negativo.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void configuracoesDeNotificacaoIdaEVolta() throws Exception {
        mockMvc.perform(get("/api/settings").with(usuario(usuarioB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
                .andExpect(jsonPath("$.channels").value(org.hamcrest.Matchers.contains("PUSH")))
                .andExpect(jsonPath("$.defaultReminderMinutes").doesNotExist())
                .andExpect(jsonPath("$.digestTime").doesNotExist());

        configurar(usuarioB, "{\"timezone\":\"Europe/Lisbon\",\"channels\":[\"EMAIL\",\"PUSH\"],\"defaultReminderMinutes\":15,\"digestTime\":\"07:00\"}");

        mockMvc.perform(get("/api/settings").with(usuario(usuarioB)))
                .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.channels").value(org.hamcrest.Matchers.containsInAnyOrder("EMAIL", "PUSH")))
                .andExpect(jsonPath("$.defaultReminderMinutes").value(15))
                .andExpect(jsonPath("$.digestTime").value("07:00"));

        mockMvc.perform(put("/api/settings").with(usuario(usuarioB)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Marte/Olympus\",\"channels\":[\"PUSH\"]}"))
                .andExpect(status().isBadRequest());
    }
}
