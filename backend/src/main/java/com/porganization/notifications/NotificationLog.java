package com.porganization.notifications;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Registro do que foi enviado (tabela notification_log). claim() reserva o envio de forma atômica
 * (insert on conflict do nothing): só quem reserva envia, então dois crons ao mesmo tempo não duplicam.
 * Se o envio falhar, release() libera para a próxima chamada tentar de novo.
 */
@Component
public class NotificationLog {

    private final JdbcTemplate jdbc;

    public NotificationLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(Reminder reminder, LocalDate occurrenceDate, ChannelType channel) {
        return jdbc.update("""
                insert into notification_log (user_id, reminder_id, occurrence_date, channel) values (?, ?, ?, ?)
                on conflict (reminder_id, occurrence_date, channel) do nothing
                """, reminder.getUserId(), reminder.getId(), occurrenceDate, channel.name()) == 1;
    }

    public void release(UUID reminderId, LocalDate occurrenceDate, ChannelType channel) {
        jdbc.update("delete from notification_log where reminder_id = ? and occurrence_date = ? and channel = ?",
                reminderId, occurrenceDate, channel.name());
    }
}
