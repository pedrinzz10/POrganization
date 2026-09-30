package com.porganization.integrations.google;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GoogleImportIT extends GoogleIT {

    private static final String EVENTOS = "/calendar/v3/calendars/primary/events";

    @Autowired
    private TokenCrypto crypto;

    @Autowired
    private GoogleImportService importer;

    private final UUID userId = UUID.randomUUID();

    /** Só este usuário conectado (o cron importa de todos), com token de acesso válido. */
    @BeforeEach
    void conectado() {
        jdbc.update("delete from google_connections");
        jdbc.update("""
                insert into google_connections (user_id, refresh_token_enc, access_token_enc, access_expires_at)
                values (?, ?, ?, ?)
                """, userId, crypto.encrypt("refresh"), crypto.encrypt("access-valido"),
                java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(3650))));
    }

    private static String evento(String id, String status, String titulo) {
        return """
                {"id":"%s","status":"%s","summary":"%s","location":"Consultório",
                 "start":{"dateTime":"2026-10-05T18:00:00Z"},"end":{"dateTime":"2026-10-05T19:00:00Z"}}
                """.formatted(id, status, titulo);
    }

    private static String pagina(String proximoToken, String... eventos) {
        return "{\"items\":[" + String.join(",", eventos) + "],\"nextSyncToken\":\"" + proximoToken + "\"}";
    }

    private void completa(String corpo) {
        google.stubFor(get(urlPathEqualTo(EVENTOS)).withQueryParam("syncToken", absent())
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    private void incremental(String token, int status, String corpo) {
        google.stubFor(get(urlPathEqualTo(EVENTOS)).withQueryParam("syncToken", equalTo(token))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(corpo)));
    }

    private List<Map<String, Object>> importados() {
        return jdbc.queryForList("""
                select title, date::text as date, start_time::text as start_time, end_time::text as end_time, location, google_event_id
                from commitments where user_id = ? and source = 'GOOGLE' order by google_event_id
                """, userId);
    }

    private String syncToken() {
        return jdbc.queryForObject("select sync_token from google_connections where user_id = ?", String.class, userId);
    }

    // I08 T1 (CA1)
    @Test
    void eventoNovoViraCompromissoEEventoCanceladoORemove() throws Exception {
        completa(pagina("token-1", evento("g1", "confirmed", "Consulta")));
        incremental("token-1", 200, pagina("token-2", evento("g1", "cancelled", "Consulta")));

        // Pelo cron, como em produção
        mockMvc.perform(post("/internal/reminders/dispatch").header("X-Cron-Secret", CRON_SECRET)).andExpect(status().isOk());

        assertThat(importados()).singleElement().satisfies(c -> {
            assertThat(c).containsEntry("title", "Consulta").containsEntry("google_event_id", "g1").containsEntry("location", "Consultório");
            // 18:00Z = 15:00 em São Paulo
            assertThat(c).containsEntry("date", "2026-10-05").containsEntry("start_time", "15:00:00").containsEntry("end_time", "16:00:00");
        });
        assertThat(syncToken()).isEqualTo("token-1");

        importer.importFor(userId);

        assertThat(importados()).isEmpty();
        assertThat(syncToken()).isEqualTo("token-2");
    }

    // I08 T2 (CA2)
    @Test
    void eventoCriadoPeloProprioAppNaoVolta() {
        completa(pagina("token-1", """
                {"id":"g-app","status":"confirmed","summary":"Aula de Java","start":{"dateTime":"2026-10-05T22:00:00Z"},
                 "extendedProperties":{"private":{"porganizationId":"%s"}}}
                """.formatted(UUID.randomUUID())));

        importer.importFor(userId);

        assertThat(jdbc.queryForObject("select count(*) from commitments where user_id = ?", Integer.class, userId)).isZero();
    }

    // I08 T3 (CA3)
    @Test
    void syncTokenExpirado410FazSincronizacaoCompletaSemDuplicar() {
        completa(pagina("token-1", evento("g1", "confirmed", "Consulta")));
        importer.importFor(userId);
        assertThat(importados()).hasSize(1);

        incremental("token-1", 410, "{\"error\":{\"code\":410,\"message\":\"Sync token is no longer valid\"}}");
        completa(pagina("token-novo", evento("g1", "confirmed", "Consulta"), evento("g2", "confirmed", "Reunião")));

        importer.importFor(userId);

        assertThat(importados()).extracting(c -> c.get("google_event_id")).containsExactly("g1", "g2");
        assertThat(syncToken()).isEqualTo("token-novo");
    }

    @Test
    void diaTodoESerieSemanalDoGoogle() {
        completa(pagina("t", """
                {"id":"g-ferias","status":"confirmed","summary":"Férias","start":{"date":"2026-12-20"},"end":{"date":"2026-12-21"}}
                """, """
                {"id":"g-ingles","status":"confirmed","summary":"Inglês","start":{"dateTime":"2026-10-05T19:00:00-03:00"},
                 "end":{"dateTime":"2026-10-05T20:00:00-03:00"},"recurrence":["RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=10"]}
                """));

        importer.importFor(userId);

        Map<String, Object> ferias = jdbc.queryForMap("select all_day, start_time from commitments where google_event_id = 'g-ferias' and user_id = ?", userId);
        assertThat(ferias).containsEntry("all_day", true).containsEntry("start_time", null);
        String regra = jdbc.queryForObject("select recurrence_rule::text from commitments where google_event_id = 'g-ingles' and user_id = ?",
                String.class, userId);
        assertThat(regra).contains("\"freq\": \"WEEKLY\"").contains("\"count\": 10").contains("MON").contains("WED");
    }

    @Test
    void mudancaNoGoogleAtualizaOCompromissoImportado() {
        completa(pagina("token-1", evento("g1", "confirmed", "Consulta")));
        incremental("token-1", 200, pagina("token-2", evento("g1", "confirmed", "Consulta remarcada")));

        importer.importFor(userId);
        importer.importFor(userId);

        assertThat(importados()).singleElement().satisfies(c -> assertThat(c).containsEntry("title", "Consulta remarcada"));
    }
}
