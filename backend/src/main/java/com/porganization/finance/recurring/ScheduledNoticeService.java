package com.porganization.finance.recurring;

import com.porganization.finance.recurring.ScheduledDtos.Occurrence;
import com.porganization.finance.transactions.TransactionType;
import com.porganization.notifications.ChannelType;
import com.porganization.notifications.Notification;
import com.porganization.notifications.NotificationChannel;
import com.porganization.settings.UserSettings;
import com.porganization.settings.UserSettingsRepository;
import com.porganization.settings.UserSettingsService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Aviso diário dos agendados para confirmar (F20): no primeiro cron a partir do horário escolhido
 * nas Configurações, um aviso só, pelos canais padrão, listando o que é para hoje e o que está
 * atrasado. Sem itens, não manda nada. Um por dia (last_scheduled_notice_date, marcado antes de enviar).
 */
@Service
public class ScheduledNoticeService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledNoticeService.class);
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final UserSettingsRepository settings;
    private final UserSettingsService userSettings;
    private final ScheduledService scheduled;
    private final List<NotificationChannel> channels;
    private final Clock clock;

    public ScheduledNoticeService(UserSettingsRepository settings, UserSettingsService userSettings, ScheduledService scheduled,
            List<NotificationChannel> channels, Clock clock) {
        this.settings = settings;
        this.userSettings = userSettings;
        this.scheduled = scheduled;
        this.channels = channels;
        this.clock = clock;
    }

    /** Devolve quantos avisos saíram. */
    public int run() {
        int sent = 0;
        for (UserSettings s : settings.findByScheduledNoticeTimeIsNotNull()) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), userSettings.zoneOf(s.getUserId()));
            LocalDate day = now.toLocalDate();
            boolean alreadySent = s.getLastScheduledNoticeDate() != null && !s.getLastScheduledNoticeDate().isBefore(day);
            if (now.toLocalTime().isBefore(s.getScheduledNoticeTime()) || alreadySent) {
                continue;
            }
            List<Occurrence> pending = scheduled.pending(s.getUserId());
            if (pending.isEmpty() || settings.claimScheduledNotice(s.getUserId(), day) == 0) {
                continue;
            }
            if (deliver(s, compose(s.getUserId(), pending))) {
                sent++;
            } else {
                settings.releaseScheduledNotice(s.getUserId(), day, s.getLastScheduledNoticeDate());
            }
        }
        return sent;
    }

    /** "Para confirmar: 2 agendados" e uma linha por item ("Salário · R$ 3.200,00 · Bradesco (atrasado)"). */
    static Notification compose(UUID userId, List<Occurrence> pending) {
        String subject = "Para confirmar: " + pending.size() + (pending.size() == 1 ? " agendado" : " agendados");
        List<String> lines = pending.stream()
                .map(o -> "%s · %s%s%s".formatted(
                        o.description() == null ? (o.type() == TransactionType.INCOME ? "Recebimento" : "Pagamento") : o.description(),
                        money(o.amount()),
                        o.accountName() == null ? "" : " · " + o.accountName(),
                        o.status() == OccurrenceStatus.OVERDUE ? " (atrasado)" : ""))
                .toList();
        return new Notification(userId, Notification.Kind.SCHEDULED_NOTICE, subject, lines, "/financas/agendados");
    }

    private boolean deliver(UserSettings s, Notification notice) {
        boolean delivered = false;
        for (String name : s.getNotifyChannels()) {
            ChannelType type = ChannelType.valueOf(name);
            Optional<NotificationChannel> channel = channels.stream().filter(c -> c.type() == type).findFirst();
            if (channel.isEmpty()) {
                continue;
            }
            try {
                channel.get().send(notice);
                delivered = true;
            } catch (Exception e) {
                log.error("Falha ao enviar o aviso de agendados por {} para o usuário {}: {}", type, s.getUserId(), e.getMessage(), e);
            }
        }
        return delivered;
    }

    private static String money(BigDecimal value) {
        return String.format(PT_BR, "R$ %,.2f", value);
    }
}
