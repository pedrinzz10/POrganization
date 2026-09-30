package com.porganization.notifications;

import com.porganization.commitments.dto.OccurrenceResponse;
import com.porganization.finance.budgets.BudgetDtos.BudgetLevel;
import com.porganization.finance.today.FinanceToday.DueItem;
import com.porganization.settings.UserSettings;
import com.porganization.settings.UserSettingsRepository;
import com.porganization.settings.UserSettingsService;
import com.porganization.studies.DailyStudyPlanner.ReviewSuggestion;
import com.porganization.today.TodayResponse;
import com.porganization.today.TodayService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resumo diário: no primeiro cron a partir do horário escolhido (fuso do usuário), manda por push
 * e/ou e-mail o que tem no dia (compromissos, revisões, contas e faturas vencendo), montado pelo
 * mesmo TodayService da tela Hoje. Um por dia: last_digest_date é marcado antes de enviar.
 */
@Service
public class DailyDigestService {

    private static final Logger log = LoggerFactory.getLogger(DailyDigestService.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final int MAX_LINES_PER_GROUP = 5;

    private final UserSettingsRepository settings;
    private final UserSettingsService userSettings;
    private final TodayService today;
    private final List<NotificationChannel> channels;
    private final Clock clock;

    public DailyDigestService(UserSettingsRepository settings, UserSettingsService userSettings, TodayService today,
            List<NotificationChannel> channels, Clock clock) {
        this.settings = settings;
        this.userSettings = userSettings;
        this.today = today;
        this.channels = channels;
        this.clock = clock;
    }

    /** Envia os resumos que já deram a hora hoje e ainda não saíram. Devolve quantos foram enviados. */
    public int run() {
        int sent = 0;
        for (UserSettings s : settings.findByDigestTimeIsNotNull()) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), userSettings.zoneOf(s.getUserId()));
            LocalDate day = now.toLocalDate();
            boolean alreadySent = s.getLastDigestDate() != null && !s.getLastDigestDate().isBefore(day);
            if (now.toLocalTime().isBefore(s.getDigestTime()) || alreadySent || settings.claimDigest(s.getUserId(), day) == 0) {
                continue;
            }
            Optional<Notification> digest = compose(s.getUserId(), today.today(s.getUserId()));
            if (digest.isEmpty()) {
                // Dia vazio: nada a avisar, e o dia conta como resolvido
                continue;
            }
            if (deliver(s, digest.get())) {
                sent++;
            } else {
                settings.releaseDigest(s.getUserId(), day, s.getLastDigestDate());
            }
        }
        return sent;
    }

    private boolean deliver(UserSettings s, Notification digest) {
        boolean delivered = false;
        for (String name : s.getNotifyChannels()) {
            ChannelType type = ChannelType.valueOf(name);
            Optional<NotificationChannel> channel = channels.stream().filter(c -> c.type() == type).findFirst();
            if (channel.isEmpty()) {
                continue;
            }
            try {
                channel.get().send(digest);
                delivered = true;
            } catch (Exception e) {
                log.error("Falha ao enviar resumo diário por {} para o usuário {}: {}", type, s.getUserId(), e.getMessage(), e);
            }
        }
        return delivered;
    }

    /**
     * Assunto com as contagens ("Seu dia: 2 compromissos, 3 revisões, 1 vencimento") e uma linha por
     * item. Sem nada no dia, nem orçamento em alerta, não há resumo.
     */
    static Optional<Notification> compose(UUID userId, TodayResponse t) {
        List<OccurrenceResponse> commitments = t.commitments().stream().filter(o -> !o.done()).toList();
        List<ReviewSuggestion> reviews = t.studies().reviews();
        List<DueItem> due = t.finance().dueSoon();
        var alerts = t.finance().budgetAlerts();
        if (commitments.isEmpty() && reviews.isEmpty() && due.isEmpty() && alerts.isEmpty()) {
            return Optional.empty();
        }

        String subject = "Seu dia: %s, %s, %s".formatted(
                plural(commitments.size(), "compromisso", "compromissos"),
                plural(reviews.size(), "revisão", "revisões"),
                plural(due.size(), "vencimento", "vencimentos"));

        List<String> lines = new ArrayList<>();
        commitments.stream().limit(MAX_LINES_PER_GROUP).forEach(o -> lines.add(
                (o.allDay() || o.startTime() == null ? "Dia todo" : o.startTime().format(HOUR)) + " · " + o.title()));
        reviews.stream().limit(MAX_LINES_PER_GROUP).forEach(r -> lines.add("Revisar " + r.lessonTitle() + " (" + r.subjectName() + ")"));
        due.stream().limit(MAX_LINES_PER_GROUP).forEach(d -> lines.add(
                d.title() + ": " + money(d.amount()) + (d.dueDate().equals(t.date()) ? " vence hoje" : " vence " + d.dueDate().format(DAY_MONTH))));
        alerts.forEach(b -> lines.add("Orçamento de " + b.categoryName()
                + (b.level() == BudgetLevel.ESTOURADO ? " estourado" : " perto do limite")));
        return Optional.of(new Notification(userId, Notification.Kind.DAILY_DIGEST, subject, lines, "/hoje"));
    }

    static String plural(int count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    private static String money(BigDecimal value) {
        return String.format(PT_BR, "R$ %,.2f", value);
    }
}
