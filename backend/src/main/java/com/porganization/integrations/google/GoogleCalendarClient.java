package com.porganization.integrations.google;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Chamadas à Google Calendar API v3 (eventos de uma agenda), com o token de acesso do usuário. */
@Component
public class GoogleCalendarClient {

    private final RestClient http;
    private final GoogleProperties google;
    private final ObjectMapper json;

    public GoogleCalendarClient(RestClient googleRestClient, GoogleProperties google, ObjectMapper json) {
        this.http = googleRestClient;
        this.google = google;
        this.json = json;
    }

    /** Cria o evento e devolve o id dele no Google. */
    public String insert(String accessToken, String calendarId, Map<String, Object> event) {
        String body = http.post().uri(google.calendarApi() + "/calendars/{calendar}/events", calendarId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(event))
                .retrieve()
                .body(String.class);
        return json.readTree(body).path("id").asString();
    }

    public void patch(String accessToken, String calendarId, String eventId, Map<String, Object> event) {
        http.patch().uri(google.calendarApi() + "/calendars/{calendar}/events/{event}", calendarId, eventId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(event))
                .retrieve()
                .toBodilessEntity();
    }

    /** Exclui o evento; se ele já não existe no Google (404/410), está tudo certo. */
    public void delete(String accessToken, String calendarId, String eventId) {
        try {
            http.delete().uri(google.calendarApi() + "/calendars/{calendar}/events/{event}", calendarId, eventId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Gone e) {
            // já removido
        }
    }

    /**
     * Uma página de eventos (I08): com syncToken, só o que mudou desde a última sincronização; sem ele,
     * tudo (e a última página traz o nextSyncToken). singleEvents=false mantém as séries como uma só.
     */
    public JsonNode list(String accessToken, String calendarId, String syncToken, String pageToken) {
        StringBuilder uri = new StringBuilder(google.calendarApi()).append("/calendars/{calendar}/events?showDeleted=true&maxResults=250");
        if (syncToken != null) {
            uri.append("&syncToken={sync}");
        }
        if (pageToken != null) {
            uri.append("&pageToken={page}");
        }
        String body = http.get().uri(uri.toString(), uriVariables(calendarId, syncToken, pageToken))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(String.class);
        return json.readTree(body);
    }

    private static Map<String, String> uriVariables(String calendarId, String syncToken, String pageToken) {
        Map<String, String> variables = new java.util.HashMap<>();
        variables.put("calendar", calendarId);
        if (syncToken != null) {
            variables.put("sync", syncToken);
        }
        if (pageToken != null) {
            variables.put("page", pageToken);
        }
        return variables;
    }
}
