package com.porganization.integrations.google;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class CommitmentSyncIT extends GoogleIT {

    private static final String EVENTOS = "/calendar/v3/calendars/primary/events";

    @Autowired
    private TokenCrypto crypto;

    private final UUID userId = UUID.randomUUID();

    /** Usuário já conectado, com um token de acesso válido (não precisa renovar). */
    @BeforeEach
    void conectado() {
        jdbc.update("""
                insert into google_connections (user_id, google_email, refresh_token_enc, access_token_enc, access_expires_at)
                values (?, 'pedro@gmail.com', ?, ?, ?)
                """, userId, crypto.encrypt("refresh"), crypto.encrypt("access-valido"),
                java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(3650))));
    }

    private String criar(String json) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/api/commitments").with(usuario(userId))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private Map<String, Object> linha(String id) {
        return jdbc.queryForMap("select google_event_id, sync_pending from commitments where id = ?::uuid", id);
    }

    private static void esperar(Runnable verificacao) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(verificacao::run);
    }

    // I07 T1 (CA1)
    @Test
    void criarEditarEExcluirViramPostPatchEDeleteNoMesmoEvento() throws Exception {
        google.stubFor(post(EVENTOS).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"id\":\"evento-1\"}")));
        google.stubFor(patch(urlEqualTo(EVENTOS + "/evento-1")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"id\":\"evento-1\"}")));
        google.stubFor(delete(urlEqualTo(EVENTOS + "/evento-1")).willReturn(aResponse().withStatus(204)));

        String id = criar("""
                {"title":"Aula de Java","date":"2026-10-05","startTime":"19:00","endTime":"21:00",
                 "recurrenceRule":{"freq":"WEEKLY","interval":1,"byWeekDays":["MON","WED"],"until":"2026-12-31"}}
                """);
        esperar(() -> assertThat(linha(id).get("google_event_id")).isEqualTo("evento-1"));
        google.verify(postRequestedFor(urlEqualTo(EVENTOS))
                .withHeader("Authorization", equalTo("Bearer access-valido"))
                .withRequestBody(matchingJsonPath("$.summary", equalTo("Aula de Java")))
                .withRequestBody(matchingJsonPath("$.start.dateTime", equalTo("2026-10-05T19:00:00")))
                .withRequestBody(matchingJsonPath("$.start.timeZone", equalTo("America/Sao_Paulo")))
                .withRequestBody(matchingJsonPath("$.recurrence[0]", equalTo("RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261231T235959Z")))
                .withRequestBody(matchingJsonPath("$.extendedProperties.private.porganizationId", equalTo(id))));

        mockMvc.perform(put("/api/commitments/" + id).with(usuario(userId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Aula de Java (sala 3)\",\"date\":\"2026-10-05\",\"startTime\":\"19:00\"}"))
                .andExpect(status().isOk());
        esperar(() -> google.verify(patchRequestedFor(urlEqualTo(EVENTOS + "/evento-1"))
                .withRequestBody(matchingJsonPath("$.summary", equalTo("Aula de Java (sala 3)")))));

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/commitments/" + id).with(usuario(userId)))
                .andExpect(status().isNoContent());
        esperar(() -> google.verify(deleteRequestedFor(urlEqualTo(EVENTOS + "/evento-1"))));
        google.verify(1, postRequestedFor(urlEqualTo(EVENTOS)));
    }

    // I07 T3 (CA3)
    @Test
    void googleForaDoArNaoImpedeSalvarEOCronSincronizaDepois() throws Exception {
        google.stubFor(post(EVENTOS).willReturn(aResponse().withStatus(500)));

        String id = criar("{\"title\":\"Dentista\",\"date\":\"2026-10-05\",\"startTime\":\"15:00\"}");
        esperar(() -> assertThat(linha(id).get("sync_pending")).isEqualTo(true));

        google.stubFor(post(EVENTOS).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"id\":\"evento-2\"}")));
        mockMvc.perform(MockMvcRequestBuilders.post("/internal/reminders/dispatch").header("X-Cron-Secret", CRON_SECRET))
                .andExpect(status().isOk());

        assertThat(linha(id)).containsEntry("sync_pending", false).containsEntry("google_event_id", "evento-2");
    }

    @Test
    void diaTodoVaiComDataEFimExclusivo() throws Exception {
        google.stubFor(post(EVENTOS).willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{\"id\":\"evento-3\"}")));

        String id = criar("{\"title\":\"Aniversário\",\"date\":\"2026-10-05\"}");

        esperar(() -> assertThat(linha(id).get("google_event_id")).isEqualTo("evento-3"));
        google.verify(postRequestedFor(urlEqualTo(EVENTOS))
                .withRequestBody(matchingJsonPath("$.start.date", equalTo("2026-10-05")))
                .withRequestBody(matchingJsonPath("$.end.date", equalTo("2026-10-06"))));
    }

    @Test
    void usuarioSemConexaoNaoChamaOGoogle() throws Exception {
        UUID semGoogle = UUID.randomUUID();
        mockMvc.perform(MockMvcRequestBuilders.post("/api/commitments").with(usuario(semGoogle))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Local\",\"date\":\"2026-10-05\"}"))
                .andExpect(status().isCreated());

        Thread.sleep(300);
        google.verify(0, postRequestedFor(urlEqualTo(EVENTOS)));
    }
}
