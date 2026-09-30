package com.porganization.notifications;

import com.porganization.commitments.CommitmentService;
import com.porganization.commitments.dto.OccurrenceResponse;
import com.porganization.settings.UserSettingsService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Envia os lembretes devidos. Chamado pelo cron externo a cada ~5 minutos (InternalCronController):
 * dispara todo lembrete cujo horário (ocorrência − minutesBefore) caiu na janela [agora − 10 min, agora].
 * <ul>
 * <li>As ocorrências vêm do CommitmentService, então séries recorrentes, horários trocados e
 * ocorrências canceladas já vêm resolvidos; concluídas não disparam.</li>
 * <li>Compromisso de dia todo conta como às 09:00.</li>
 * <li>Cada (lembrete, ocorrência, canal) é reservado no NotificationLog antes de enviar: chamar de
 * novo não duplica. Falha num envio é registrada no log e libera a reserva; os outros seguem.</li>
 * </ul>
 */
@Service
public class ReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatcher.class);

    static final Duration WINDOW = Duration.ofMinutes(10);
    static final LocalTime ALL_DAY_TIME = LocalTime.of(9, 0);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("pt-BR"));
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final ReminderRepository reminders;
    private final CommitmentService commitments;
    private final UserSettingsService userSettings;
    private final NotificationLog notificationLog;
    private final List<NotificationChannel> channels;
    private final Clock clock;

    public ReminderDispatcher(ReminderRepository reminders, CommitmentService commitments, UserSettingsService userSettings,
            NotificationLog notificationLog, List<NotificationChannel> channels, Clock clock) {
        this.reminders = reminders;
        this.commitments = commitments;
        this.userSettings = userSettings;
        this.notificationLog = notificationLog;
        this.channels = channels;
        this.clock = clock;
    }

    public record Result(int sent, int failed) {
    }

    public Result dispatch() {
        Instant now = clock.instant();
        int sent = 0;
        int failed = 0;
        Map<UUID, List<Reminder>> byUser = reminders.findAll().stream().collect(Collectors.groupingBy(Reminder::getUserId));
        for (Map.Entry<UUID, List<Reminder>> entry : byUser.entrySet()) {
            for (Due due : dueFor(entry.getKey(), entry.getValue(), now)) {
                for (ChannelType type : due.reminder().getChannels()) {
                    if (!notificationLog.claim(due.reminder(), due.occurrence().occurrenceDate(), type)) {
                        continue;
                    }
                    if (send(type, notificationFor(due))) {
                        sent++;
                    } else {
                        notificationLog.release(due.reminder().getId(), due.occurrence().occurrenceDate(), type);
                        failed++;
                    }
                }
            }
        }
        return new Result(sent, failed);
    }

    /** Ocorrências do usuário cujo horário de aviso caiu na janela. */
    private List<Due> dueFor(UUID userId, List<Reminder> userReminders, Instant now) {
        ZoneId zone = userSettings.zoneOf(userId);
        int maxMinutes = userReminders.stream().mapToInt(Reminder::getMinutesBefore).max().orElse(0);
        // O aviso é ocorrência − minutos: a ocorrência fica entre agora − 10 min e agora + o maior lembrete
        LocalDate from = LocalDate.ofInstant(now.minus(WINDOW), zone);
        LocalDate to = LocalDate.ofInstant(now.plus(Duration.ofMinutes(maxMinutes)), zone);
        Map<UUID, List<OccurrenceResponse>> byCommitment = commitments.findInRange(userId, from, to).stream()
                .collect(Collectors.groupingBy(OccurrenceResponse::commitmentId));

        List<Due> due = new ArrayList<>();
        for (Reminder reminder : userReminders) {
            for (OccurrenceResponse occurrence : byCommitment.getOrDefault(reminder.getCommitmentId(), List.of())) {
                if (occurrence.done()) {
                    continue;
                }
                LocalTime time = occurrence.allDay() || occurrence.startTime() == null ? ALL_DAY_TIME : occurrence.startTime();
                Instant fireAt = ZonedDateTime.of(occurrence.occurrenceDate(), time, zone).toInstant()
                        .minus(Duration.ofMinutes(reminder.getMinutesBefore()));
                if (!fireAt.isBefore(now.minus(WINDOW)) && !fireAt.isAfter(now)) {
                    due.add(new Due(reminder, occurrence));
                }
            }
        }
        return due;
    }

    private boolean send(ChannelType type, Notification notification) {
        Optional<NotificationChannel> channel = channels.stream().filter(c -> c.type() == type).findFirst();
        if (channel.isEmpty()) {
            log.warn("Canal {} não configurado; lembrete do usuário {} não enviado", type, notification.userId());
            return false;
        }
        try {
            channel.get().send(notification);
            return true;
        } catch (Exception e) {
            log.error("Falha ao enviar lembrete por {} para o usuário {}: {}", type, notification.userId(), e.getMessage(), e);
            return false;
        }
    }

    /** Assunto "Lembrete: Dentista às 15:00" (ou "Lembrete: Aniversário hoje" para dia todo). */
    static Notification notificationFor(Due due) {
        OccurrenceResponse o = due.occurrence();
        boolean allDay = o.allDay() || o.startTime() == null;
        String when = allDay ? "hoje" : "às " + o.startTime().format(HOUR);
        String day = o.occurrenceDate().format(DAY);
        List<String> lines = new ArrayList<>();
        lines.add(allDay ? day + " (dia todo)" : day + " às " + o.startTime().format(HOUR));
        if (o.location() != null) {
            lines.add(o.location());
        }
        return new Notification(due.reminder().getUserId(), Notification.Kind.REMINDER,
                "Lembrete: %s %s".formatted(o.title(), when), lines, "/hoje");
    }

    record Due(Reminder reminder, OccurrenceResponse occurrence) {
    }
}
