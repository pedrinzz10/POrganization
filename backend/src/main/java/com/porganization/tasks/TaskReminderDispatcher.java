package com.porganization.tasks;

import com.porganization.notifications.ChannelType;
import com.porganization.notifications.Notification;
import com.porganization.notifications.NotificationChannel;
import com.porganization.settings.UserSettings;
import com.porganization.settings.UserSettingsRepository;
import com.porganization.settings.UserSettingsService;
import com.porganization.tasks.DailyTaskDtos.DayTask;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Lembrete das tarefas diárias (T05): quando o horário da tarefa cai na janela do cron (de reminder_time
 * até 10 minutos depois, no fuso do usuário) e ela, devida hoje, ainda não foi feita, avisa pelos canais
 * padrão das Configurações. Um por tarefa e dia: last_reminder_date é marcado antes de enviar e volta
 * se nenhum canal entregar.
 */
@Service
public class TaskReminderDispatcher {

    /** Tamanho da janela; o cron roda a cada ~5 minutos. */
    static final Duration WINDOW = Duration.ofMinutes(10);

    private static final Logger log = LoggerFactory.getLogger(TaskReminderDispatcher.class);

    private final DailyTaskRepository tasks;
    private final DailyTaskService taskService;
    private final UserSettingsRepository settings;
    private final UserSettingsService userSettings;
    private final List<NotificationChannel> channels;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TaskReminderDispatcher(DailyTaskRepository tasks, DailyTaskService taskService, UserSettingsRepository settings,
            UserSettingsService userSettings, List<NotificationChannel> channels, JdbcTemplate jdbc, Clock clock) {
        this.tasks = tasks;
        this.taskService = taskService;
        this.settings = settings;
        this.userSettings = userSettings;
        this.channels = channels;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Devolve quantos lembretes saíram. */
    public int run() {
        Map<UUID, List<DailyTask>> byUser = tasks.findByReminderTimeIsNotNullAndArchivedFalse().stream()
                .collect(Collectors.groupingBy(DailyTask::getUserId));
        int sent = 0;
        for (var entry : byUser.entrySet()) {
            UUID userId = entry.getKey();
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), userSettings.zoneOf(userId));
            LocalDate day = now.toLocalDate();
            List<DailyTask> inWindow = entry.getValue().stream().filter(t -> inWindow(t.getReminderTime(), now.toLocalTime())).toList();
            if (inWindow.isEmpty()) {
                continue;
            }
            Map<UUID, DayTask> dueToday = taskService.day(userId, day).stream().collect(Collectors.toMap(DayTask::id, d -> d));
            Optional<UserSettings> userSetting = settings.findById(userId);
            for (DailyTask task : inWindow) {
                DayTask due = dueToday.get(task.getId());
                if (due == null || due.done() || userSetting.isEmpty() || !claim(task.getId(), day)) {
                    continue;
                }
                if (deliver(userSetting.get(), compose(userId, due))) {
                    sent++;
                } else {
                    release(task.getId(), day);
                }
            }
        }
        return sent;
    }

    /** reminder ≤ agora < reminder + janela (sem virar o dia: um lembrete às 23:55 vale até 23:59). */
    static boolean inWindow(LocalTime reminder, LocalTime now) {
        LocalTime end = reminder.plus(WINDOW);
        return !now.isBefore(reminder) && (end.isBefore(reminder) || now.isBefore(end));
    }

    /** "Lembrete: 💧 Beber água" → abre a tela Hoje. */
    static Notification compose(UUID userId, DayTask task) {
        String name = (task.emoji() == null ? "" : task.emoji() + " ") + task.title();
        return new Notification(userId, Notification.Kind.TASK_REMINDER, "Lembrete: " + name,
                List.of("Ainda não marcada como feita hoje."), "/hoje");
    }

    private boolean claim(UUID taskId, LocalDate day) {
        return jdbc.update("update daily_tasks set last_reminder_date = ? where id = ?"
                + " and (last_reminder_date is null or last_reminder_date < ?)", Date.valueOf(day), taskId, Date.valueOf(day)) == 1;
    }

    private void release(UUID taskId, LocalDate day) {
        jdbc.update("update daily_tasks set last_reminder_date = null where id = ? and last_reminder_date = ?", taskId, Date.valueOf(day));
    }

    private boolean deliver(UserSettings s, Notification notification) {
        boolean delivered = false;
        for (String name : s.getNotifyChannels()) {
            ChannelType type = ChannelType.valueOf(name);
            Optional<NotificationChannel> channel = channels.stream().filter(c -> c.type() == type).findFirst();
            if (channel.isEmpty()) {
                continue;
            }
            try {
                channel.get().send(notification);
                delivered = true;
            } catch (Exception e) {
                log.error("Falha ao enviar lembrete de tarefa por {} para o usuário {}: {}", type, s.getUserId(), e.getMessage(), e);
            }
        }
        return delivered;
    }
}
