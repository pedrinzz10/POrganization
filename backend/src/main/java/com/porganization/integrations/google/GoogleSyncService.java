package com.porganization.integrations.google;

import com.porganization.commitments.Commitment;
import com.porganization.commitments.CommitmentRepository;
import com.porganization.commitments.OccurrenceOverride;
import com.porganization.commitments.OccurrenceOverrideRepository;
import com.porganization.settings.UserSettingsService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Publica os compromissos do app no Google Calendar (I07). Criar gera um evento e guarda o id;
 * editar faz PATCH no mesmo evento; excluir faz DELETE. Recorrência vira RRULE e os dias
 * cancelados viram EXDATE. Falha na API do Google nunca desfaz o que o usuário salvou: o compromisso
 * fica com sync_pending e o cron tenta de novo (retryPending).
 */
@Service
public class GoogleSyncService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSyncService.class);

    /** Marca nos eventos criados pelo app, para a importação (I08) não trazê-los de volta. */
    public static final String APP_ID_PROPERTY = "porganizationId";

    /** RFC 3339 sem fuso (o fuso vai em timeZone), sempre com segundos. */
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GoogleConnectionRepository connections;
    private final GoogleOAuthService oauth;
    private final GoogleCalendarClient calendar;
    private final CommitmentRepository commitments;
    private final OccurrenceOverrideRepository overrides;
    private final UserSettingsService userSettings;
    private final JdbcTemplate jdbc;

    public GoogleSyncService(GoogleConnectionRepository connections, GoogleOAuthService oauth, GoogleCalendarClient calendar,
            CommitmentRepository commitments, OccurrenceOverrideRepository overrides, UserSettingsService userSettings,
            JdbcTemplate jdbc) {
        this.connections = connections;
        this.oauth = oauth;
        this.calendar = calendar;
        this.commitments = commitments;
        this.overrides = overrides;
        this.userSettings = userSettings;
        this.jdbc = jdbc;
    }

    /** Cria ou atualiza o evento do compromisso. Sem conexão com o Google, não faz nada. */
    public void sync(UUID userId, UUID commitmentId) {
        Optional<GoogleConnection> connection = connections.findById(userId);
        Optional<Commitment> found = commitments.findByIdAndUserId(commitmentId, userId);
        if (connection.isEmpty() || found.isEmpty()) {
            return;
        }
        Commitment commitment = found.get();
        try {
            String token = oauth.accessToken(userId).orElseThrow();
            String calendarId = connection.get().getCalendarId();
            Map<String, Object> event = toEvent(commitment, userSettings.zoneOf(userId));
            String eventId = commitment.getGoogleEventId();
            if (eventId == null) {
                eventId = calendar.insert(token, calendarId, event);
            } else {
                try {
                    calendar.patch(token, calendarId, eventId, event);
                } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Gone e) {
                    // Apagado direto no Google: publica de novo
                    eventId = calendar.insert(token, calendarId, event);
                }
            }
            jdbc.update("update commitments set google_event_id = ?, sync_pending = false where id = ?", eventId, commitmentId);
        } catch (RuntimeException e) {
            log.warn("Falha ao sincronizar o compromisso {} com o Google; fica pendente: {}", commitmentId, e.getMessage());
            jdbc.update("update commitments set sync_pending = true where id = ?", commitmentId);
        }
    }

    /** Remove o evento do compromisso excluído (melhor esforço: a linha já não existe para marcar pendência). */
    public void delete(UUID userId, String googleEventId) {
        if (googleEventId == null) {
            return;
        }
        connections.findById(userId).ifPresent(connection -> {
            try {
                calendar.delete(oauth.accessToken(userId).orElseThrow(), connection.getCalendarId(), googleEventId);
            } catch (RuntimeException e) {
                log.warn("Falha ao excluir o evento {} do Google: {}", googleEventId, e.getMessage());
            }
        });
    }

    /** Chamado pelo cron: tenta de novo o que ficou pendente. Devolve quantos foram sincronizados. */
    public int retryPending() {
        int synced = 0;
        for (Commitment pending : commitments.findBySyncPendingTrue()) {
            sync(pending.getUserId(), pending.getId());
            Boolean stillPending = jdbc.queryForObject("select sync_pending from commitments where id = ?", Boolean.class, pending.getId());
            if (Boolean.FALSE.equals(stillPending)) {
                synced++;
            }
        }
        return synced;
    }

    /** O compromisso no formato de evento da Calendar API. */
    Map<String, Object> toEvent(Commitment c, ZoneId zone) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("summary", c.getTitle());
        event.put("description", c.getDescription());
        event.put("location", c.getLocation());
        boolean allDay = c.isAllDay() || c.getStartTime() == null;
        if (allDay) {
            event.put("start", Map.of("date", c.getDate().toString()));
            event.put("end", Map.of("date", c.getDate().plusDays(1).toString()));
        } else {
            LocalDateTime start = LocalDateTime.of(c.getDate(), c.getStartTime());
            LocalDateTime end = c.getEndTime() != null ? LocalDateTime.of(c.getDate(), c.getEndTime()) : start.plusHours(1);
            event.put("start", Map.of("dateTime", start.format(LOCAL_DATE_TIME), "timeZone", zone.getId()));
            event.put("end", Map.of("dateTime", end.format(LOCAL_DATE_TIME), "timeZone", zone.getId()));
        }
        List<String> recurrence = new ArrayList<>();
        if (c.getRecurrenceRule() != null) {
            recurrence.add(RRuleMapper.toRRule(c.getRecurrenceRule(), allDay));
            List<LocalDate> cancelled = overrides.findByUserIdAndCommitmentIdAndCancelledTrueOrderByOccurrenceDateAsc(c.getUserId(), c.getId())
                    .stream().map(OccurrenceOverride::getOccurrenceDate).toList();
            if (!cancelled.isEmpty()) {
                recurrence.add(RRuleMapper.toExDate(cancelled, allDay ? null : c.getStartTime(), zone.getId()));
            }
        }
        // PATCH com lista vazia tira a recorrência de um evento que deixou de repetir
        event.put("recurrence", recurrence);
        event.put("extendedProperties", Map.of("private", Map.of(APP_ID_PROPERTY, c.getId().toString())));
        return event;
    }
}
