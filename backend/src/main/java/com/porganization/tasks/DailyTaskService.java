package com.porganization.tasks;

import com.porganization.commitments.recurrence.WeekDay;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.settings.UserSettingsService;
import com.porganization.tasks.DailyTaskDtos.DayTask;
import com.porganization.tasks.DailyTaskDtos.TaskRequest;
import com.porganization.tasks.DailyTaskDtos.TaskResponse;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarefas diárias (T01): cadastro, dias da semana com vigência, ordem, arquivo e marcações.
 * Marcar vale para hoje e os 7 dias anteriores, só em dia devido e a partir da criação.
 */
@Service
public class DailyTaskService {

    /** Quantos dias para trás dá para marcar ou desmarcar. */
    static final int MARK_WINDOW_DAYS = 7;

    private final DailyTaskRepository tasks;
    private final DailyTaskScheduleRepository schedules;
    private final UserSettingsService userSettings;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DailyTaskService(DailyTaskRepository tasks, DailyTaskScheduleRepository schedules, UserSettingsService userSettings,
            JdbcTemplate jdbc, Clock clock) {
        this.tasks = tasks;
        this.schedules = schedules;
        this.userSettings = userSettings;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(UUID userId) {
        List<DailyTask> all = tasks.findByUserIdOrderByPositionAscTitleAsc(userId);
        Map<UUID, TaskSchedule> byTask = schedulesOf(all);
        LocalDate today = today(userId);
        return all.stream().map(t -> toResponse(t, byTask.get(t.getId()), today)).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID userId, UUID id) {
        DailyTask task = find(userId, id);
        return toResponse(task, schedulesOf(List.of(task)).get(id), today(userId));
    }

    @Transactional
    public TaskResponse create(UUID userId, TaskRequest request) {
        Set<WeekDay> weekdays = weekdays(request.weekDays());
        LocalDate today = today(userId);
        int position = tasks.findByUserIdOrderByPositionAscTitleAsc(userId).stream().mapToInt(DailyTask::getPosition).max().orElse(0) + 1;
        DailyTask task = tasks.save(new DailyTask(userId, request.title().trim(), blankToNull(request.emoji()), position, today));
        schedules.save(new DailyTaskSchedule(task.getId(), today, weekdays));
        return toResponse(task, new TaskSchedule(today, List.of(new TaskSchedule.Rule(today, weekdays))), today);
    }

    /** Mudar os dias vale de hoje em diante; o passado segue a regra da época. */
    @Transactional
    public TaskResponse update(UUID userId, UUID id, TaskRequest request) {
        DailyTask task = find(userId, id);
        task.setTitle(request.title().trim());
        task.setEmoji(blankToNull(request.emoji()));
        LocalDate today = today(userId);
        Set<WeekDay> weekdays = weekdays(request.weekDays());
        TaskSchedule current = schedulesOf(List.of(task)).get(id);
        if (!current.weekdaysOn(today).equals(weekdays)) {
            schedules.findByTaskIdAndValidFrom(id, today)
                    .ifPresentOrElse(s -> s.setWeekdays(weekdays), () -> schedules.save(new DailyTaskSchedule(id, today, weekdays)));
        }
        return toResponse(task, schedulesOf(List.of(task)).get(id), today);
    }

    @Transactional
    public TaskResponse setArchived(UUID userId, UUID id, boolean archived) {
        DailyTask task = find(userId, id);
        task.setArchived(archived);
        return toResponse(task, schedulesOf(List.of(task)).get(id), today(userId));
    }

    /** Apaga a tarefa com as regras e as marcações (histórico some junto). */
    @Transactional
    public void delete(UUID userId, UUID id) {
        tasks.delete(find(userId, id));
    }

    /** Grava a ordem dos ids (o primeiro vira 1); ids de fora da lista vão para o fim. */
    @Transactional
    public List<TaskResponse> reorder(UUID userId, List<UUID> ids) {
        List<DailyTask> all = tasks.findByUserIdOrderByPositionAscTitleAsc(userId);
        Set<UUID> owned = all.stream().map(DailyTask::getId).collect(Collectors.toSet());
        if (!owned.containsAll(ids) || new HashSet<>(ids).size() != ids.size()) {
            throw new InvalidRequestException("ids", "lista com tarefa repetida ou que não é sua");
        }
        LinkedHashSet<UUID> order = new LinkedHashSet<>(ids);
        all.stream().map(DailyTask::getId).forEach(order::add);
        int position = 1;
        Map<UUID, DailyTask> byId = all.stream().collect(Collectors.toMap(DailyTask::getId, t -> t));
        for (UUID id : order) {
            byId.get(id).setPosition(position++);
        }
        return list(userId);
    }

    /** Tarefas ativas devidas no dia, na ordem definida, com feito ou não. */
    @Transactional(readOnly = true)
    public List<DayTask> day(UUID userId, LocalDate day) {
        List<DailyTask> active = tasks.findByUserIdAndArchivedFalseOrderByPositionAscTitleAsc(userId);
        Map<UUID, TaskSchedule> byTask = schedulesOf(active);
        Set<UUID> done = new HashSet<>(jdbc.queryForList(
                "select task_id from daily_task_completions where user_id = ? and day = ?", UUID.class, userId, Date.valueOf(day)));
        return active.stream()
                .filter(t -> byTask.get(t.getId()).isDue(day))
                .map(t -> new DayTask(t.getId(), t.getTitle(), t.getEmoji(), t.getPosition(), done.contains(t.getId())))
                .toList();
    }

    /** Marca como feita (idempotente). */
    @Transactional
    public void complete(UUID userId, UUID id, LocalDate day) {
        requireMarkable(userId, id, day);
        jdbc.update("insert into daily_task_completions (task_id, user_id, day) values (?, ?, ?) on conflict do nothing",
                id, userId, Date.valueOf(day));
    }

    /** Desmarca (idempotente). */
    @Transactional
    public void uncomplete(UUID userId, UUID id, LocalDate day) {
        requireMarkable(userId, id, day);
        jdbc.update("delete from daily_task_completions where task_id = ? and user_id = ? and day = ?", id, userId, Date.valueOf(day));
    }

    private void requireMarkable(UUID userId, UUID id, LocalDate day) {
        DailyTask task = find(userId, id);
        LocalDate today = today(userId);
        if (day.isAfter(today)) {
            throw new InvalidRequestException("date", "não dá para marcar um dia que ainda não chegou");
        }
        if (day.isBefore(today.minusDays(MARK_WINDOW_DAYS))) {
            throw new InvalidRequestException("date", "só dá para marcar hoje e os " + MARK_WINDOW_DAYS + " dias anteriores");
        }
        if (task.isArchived()) {
            throw new InvalidRequestException("id", "tarefa arquivada");
        }
        if (!schedulesOf(List.of(task)).get(id).isDue(day)) {
            throw new InvalidRequestException("date", "a tarefa não vale nesse dia");
        }
    }

    Map<UUID, TaskSchedule> schedulesOf(List<DailyTask> list) {
        Map<UUID, List<TaskSchedule.Rule>> rules = schedules.findByTaskIdIn(list.stream().map(DailyTask::getId).toList()).stream()
                .collect(Collectors.groupingBy(DailyTaskSchedule::getTaskId,
                        Collectors.mapping(DailyTaskSchedule::toRule, Collectors.toList())));
        return list.stream().collect(Collectors.toMap(DailyTask::getId,
                t -> new TaskSchedule(t.getCreatedOn(), rules.getOrDefault(t.getId(), List.of()))));
    }

    DailyTask find(UUID userId, UUID id) {
        return tasks.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Tarefa não encontrada"));
    }

    LocalDate today(UUID userId) {
        return LocalDate.ofInstant(clock.instant(), userSettings.zoneOf(userId));
    }

    private static Set<WeekDay> weekdays(Set<WeekDay> requested) {
        if (requested == null) {
            return EnumSet.allOf(WeekDay.class);
        }
        if (requested.isEmpty()) {
            throw new InvalidRequestException("weekDays", "escolha pelo menos um dia da semana");
        }
        return EnumSet.copyOf(requested);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static TaskResponse toResponse(DailyTask t, TaskSchedule schedule, LocalDate today) {
        return new TaskResponse(t.getId(), t.getTitle(), t.getEmoji(), schedule.weekdaysOn(today), t.getPosition(), t.isArchived(),
                t.getCreatedOn());
    }
}
