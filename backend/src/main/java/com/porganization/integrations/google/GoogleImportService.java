package com.porganization.integrations.google;

import com.porganization.commitments.Commitment;
import com.porganization.commitments.CommitmentRepository;
import com.porganization.commitments.recurrence.RecurrenceRule;
import com.porganization.settings.UserSettingsService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;

/**
 * Traz as mudanças da agenda do Google para o app (I08), chamado pelo cron.
 * <ul>
 * <li>Com syncToken, só o que mudou desde a última vez; sem ele (ou se o Google responder 410,
 * token expirado), uma listagem completa. A última página traz o próximo syncToken.</li>
 * <li>Evento confirmado vira (ou atualiza) um compromisso com source GOOGLE; cancelado o remove.</li>
 * <li>Evento criado pelo próprio app (extendedProperties.private.porganizationId) é ignorado,
 * então nada volta duplicado. Compromissos importados são gravados direto no repositório, sem
 * CommitmentChangedEvent, para não serem publicados de volta.</li>
 * </ul>
 */
@Service
public class GoogleImportService {

    private static final Logger log = LoggerFactory.getLogger(GoogleImportService.class);
    private static final int MAX_PAGES = 20;

    private final GoogleConnectionRepository connections;
    private final GoogleOAuthService oauth;
    private final GoogleCalendarClient calendar;
    private final CommitmentRepository commitments;
    private final UserSettingsService userSettings;
    private final TransactionTemplate transactions;

    public GoogleImportService(GoogleConnectionRepository connections, GoogleOAuthService oauth, GoogleCalendarClient calendar,
            CommitmentRepository commitments, UserSettingsService userSettings, TransactionTemplate transactions) {
        this.connections = connections;
        this.oauth = oauth;
        this.calendar = calendar;
        this.commitments = commitments;
        this.userSettings = userSettings;
        this.transactions = transactions;
    }

    /** Importa de todos os usuários conectados; falha de um não para os outros. Devolve quantos eventos aplicou. */
    public int importAll() {
        if (!oauth.available()) {
            return 0;
        }
        int applied = 0;
        for (GoogleConnection connection : connections.findAll()) {
            try {
                applied += importFor(connection.getUserId());
            } catch (RuntimeException e) {
                log.warn("Falha ao importar o Google Calendar do usuário {}: {}", connection.getUserId(), e.getMessage());
            }
        }
        return applied;
    }

    public int importFor(UUID userId) {
        GoogleConnection connection = connections.findById(userId).orElseThrow();
        String token = oauth.accessToken(userId).orElseThrow();
        Page result;
        try {
            result = listAll(token, connection.getCalendarId(), connection.getSyncToken());
        } catch (HttpClientErrorException.Gone e) {
            // syncToken expirado: recomeça do zero; o upsert pelo id do evento evita duplicar
            log.info("syncToken do Google expirado para o usuário {}; fazendo sincronização completa", userId);
            result = listAll(token, connection.getCalendarId(), null);
        }
        Page page = result;
        ZoneId zone = userSettings.zoneOf(userId);
        return transactions.execute(tx -> {
            int applied = 0;
            for (JsonNode event : page.events()) {
                if (apply(userId, event, zone)) {
                    applied++;
                }
            }
            GoogleConnection fresh = connections.findById(userId).orElseThrow();
            fresh.setSyncToken(page.nextSyncToken());
            connections.save(fresh);
            return applied;
        });
    }

    private record Page(List<JsonNode> events, String nextSyncToken) {
    }

    private Page listAll(String token, String calendarId, String syncToken) {
        List<JsonNode> events = new ArrayList<>();
        String pageToken = null;
        for (int i = 0; i < MAX_PAGES; i++) {
            JsonNode page = calendar.list(token, calendarId, syncToken, pageToken);
            page.path("items").forEach(events::add);
            pageToken = text(page, "nextPageToken");
            if (pageToken == null) {
                return new Page(events, text(page, "nextSyncToken"));
            }
        }
        return new Page(events, null);
    }

    /** Aplica um evento; devolve se mudou algo. */
    private boolean apply(UUID userId, JsonNode event, ZoneId zone) {
        String eventId = text(event, "id");
        if (eventId == null || !event.path("extendedProperties").path("private").path(GoogleSyncService.APP_ID_PROPERTY).isMissingNode()) {
            return false;
        }
        // Instância alterada de uma série: fica com a regra da série (o app não guarda exceções vindas do Google)
        if (text(event, "recurringEventId") != null) {
            return false;
        }
        Optional<Commitment> existing = commitments.findByUserIdAndGoogleEventId(userId, eventId);
        if ("cancelled".equals(text(event, "status"))) {
            existing.filter(Commitment::isFromGoogle).ifPresent(commitments::delete);
            return existing.isPresent();
        }
        if (existing.isPresent() && !existing.get().isFromGoogle()) {
            // Evento publicado pelo app sem a marca (ex.: versão antiga): o app é a fonte
            return false;
        }
        Times times = times(event, zone);
        if (times == null) {
            return false;
        }
        String title = Optional.ofNullable(text(event, "summary")).filter(t -> !t.isBlank()).orElse("(sem título)");
        Commitment commitment = existing.orElseGet(() -> Commitment.importedFromGoogle(userId, eventId, title, times.date()));
        commitment.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        commitment.setDate(times.date());
        commitment.setAllDay(times.start() == null);
        commitment.setStartTime(times.start());
        commitment.setEndTime(times.end());
        commitment.setDescription(text(event, "description"));
        commitment.setLocation(text(event, "location"));
        List<String> recurrence = new ArrayList<>();
        event.path("recurrence").forEach(r -> recurrence.add(r.asString()));
        RecurrenceRule rule = RRuleMapper.fromRRule(recurrence).orElse(null);
        commitment.setRecurrenceRule(rule);
        commitments.save(commitment);
        return true;
    }

    private record Times(LocalDate date, LocalTime start, LocalTime end) {
    }

    /** Dia todo (start.date) ou com horário (start.dateTime, levado para o fuso do usuário). */
    private static Times times(JsonNode event, ZoneId zone) {
        JsonNode start = event.path("start");
        if (start.hasNonNull("date")) {
            return new Times(LocalDate.parse(start.get("date").asString()), null, null);
        }
        if (!start.hasNonNull("dateTime")) {
            return null;
        }
        ZonedDateTime begin = OffsetDateTime.parse(start.get("dateTime").asString()).atZoneSameInstant(zone);
        LocalTime end = null;
        if (event.path("end").hasNonNull("dateTime")) {
            ZonedDateTime finish = OffsetDateTime.parse(event.path("end").get("dateTime").asString()).atZoneSameInstant(zone);
            // Só guarda o fim se for no mesmo dia e depois do início (o app não tem evento que vira a noite)
            if (finish.toLocalDate().equals(begin.toLocalDate()) && finish.toLocalTime().isAfter(begin.toLocalTime())) {
                end = finish.toLocalTime();
            }
        }
        return new Times(begin.toLocalDate(), begin.toLocalTime(), end);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
