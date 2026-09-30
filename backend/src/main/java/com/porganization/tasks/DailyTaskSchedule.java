package com.porganization.tasks;

import com.porganization.commitments.recurrence.WeekDay;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** Dias da semana de uma tarefa a partir de uma data (a mudança vale de hoje em diante). */
@Entity
@Table(name = "daily_task_schedules")
public class DailyTaskSchedule {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weekdays", nullable = false)
    private Set<WeekDay> weekdays;

    protected DailyTaskSchedule() {
    }

    public DailyTaskSchedule(UUID taskId, LocalDate validFrom, Set<WeekDay> weekdays) {
        this.taskId = taskId;
        this.validFrom = validFrom;
        setWeekdays(weekdays);
    }

    public UUID getTaskId() {
        return taskId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public Set<WeekDay> getWeekdays() {
        return weekdays;
    }

    public void setWeekdays(Set<WeekDay> weekdays) {
        this.weekdays = EnumSet.copyOf(weekdays);
    }

    public TaskSchedule.Rule toRule() {
        return new TaskSchedule.Rule(validFrom, weekdays);
    }
}
