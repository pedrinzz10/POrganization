package com.porganization.studies;

import com.porganization.common.ConflictException;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.studies.StudyCalendarPlanner.Day;
import com.porganization.studies.StudyCalendarPlanner.Kind;
import com.porganization.studies.StudyCalendarService.WeekContext;
import com.porganization.studies.StudyWeekGenerator.Slot;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plano da semana de estudos (E15). Semana sem plano mostra a previsão automática; gerar, incluir,
 * tirar ou mover uma aula transforma a semana em plano. Antes de mexer numa semana automática,
 * a previsão dela vira o plano (assim o resto da semana não muda sozinho). Limpar volta ao automático.
 * Todas as ações devolvem a semana atualizada.
 */
@Service
public class StudyWeekPlanService {

    private final StudyCalendarService calendar;
    private final SubjectRepository subjects;
    private final JdbcTemplate jdbc;

    public StudyWeekPlanService(StudyCalendarService calendar, SubjectRepository subjects, JdbcTemplate jdbc) {
        this.calendar = calendar;
        this.subjects = subjects;
        this.jdbc = jdbc;
    }

    /** Sorteia os dias das aulas da semana; o que já estava no plano de hoje em diante é trocado. */
    @Transactional
    public List<Day> generate(UUID userId, LocalDate anyDayOfWeek) {
        WeekContext context = calendar.weekContext(userId);
        LocalDate monday = editableWeek(context.today(), anyDayOfWeek);
        LocalDate first = monday.isBefore(context.today()) ? context.today() : monday;
        jdbc.update("delete from study_week_slots where user_id = ? and day between ? and ?", userId, Date.valueOf(first),
                Date.valueOf(monday.plusDays(6)));
        markPlanned(userId, monday);
        List<Slot> slots = StudyWeekGenerator.generate(context.today(), monday, context.goals(), context.lessonsThisWeek(),
                context.lessonToday(), ThreadLocalRandom.current());
        slots.forEach(s -> insert(userId, s.subjectId(), s.day()));
        return week(userId, monday);
    }

    /** Inclui uma aula da matéria no dia (dia de hoje em diante; uma por matéria por dia). */
    @Transactional
    public List<Day> add(UUID userId, UUID subjectId, LocalDate day) {
        LocalDate today = calendar.today(userId);
        LocalDate monday = editableDay(today, day);
        requireSubject(userId, subjectId);
        ensurePlanned(userId, monday, today);
        if (hasSlot(userId, subjectId, day)) {
            throw new ConflictException("Já tem aula dessa matéria nesse dia.");
        }
        insert(userId, subjectId, day);
        return week(userId, monday);
    }

    /** Tira a aula da matéria do dia. */
    @Transactional
    public List<Day> remove(UUID userId, UUID subjectId, LocalDate day) {
        LocalDate today = calendar.today(userId);
        LocalDate monday = editableDay(today, day);
        requireSubject(userId, subjectId);
        ensurePlanned(userId, monday, today);
        if (jdbc.update("delete from study_week_slots where user_id = ? and subject_id = ? and day = ?", userId, subjectId,
                Date.valueOf(day)) == 0) {
            throw new ConflictException("Essa aula não está mais nesse dia. Atualize a agenda.");
        }
        return week(userId, monday);
    }

    /** Arrastar na agenda: a aula da matéria vai de "from" para "to", na mesma semana. */
    @Transactional
    public List<Day> move(UUID userId, UUID subjectId, LocalDate from, LocalDate to) {
        LocalDate today = calendar.today(userId);
        if (from.isBefore(today) || to.isBefore(today)) {
            throw new InvalidRequestException("to", "só dá para mover aulas de hoje em diante");
        }
        LocalDate monday = StudyCalendarService.mondayOf(from);
        if (!StudyCalendarService.mondayOf(to).equals(monday)) {
            throw new InvalidRequestException("to", "mova a aula dentro da mesma semana");
        }
        Subject subject = requireSubject(userId, subjectId);
        ensurePlanned(userId, monday, today);
        if (from.equals(to)) {
            return week(userId, monday);
        }
        if (!hasSlot(userId, subjectId, from)) {
            throw new ConflictException("Essa aula não está mais nesse dia. Atualize a agenda.");
        }
        if (hasSlot(userId, subjectId, to)) {
            throw new ConflictException("Já tem aula de " + subject.getName() + " nesse dia.");
        }
        jdbc.update("update study_week_slots set day = ? where user_id = ? and subject_id = ? and day = ?", Date.valueOf(to),
                userId, subjectId, Date.valueOf(from));
        return week(userId, monday);
    }

    /** Volta a semana à previsão automática (apaga o plano e as aulas fixadas dela). */
    @Transactional
    public void clear(UUID userId, LocalDate anyDayOfWeek) {
        LocalDate monday = StudyCalendarService.mondayOf(anyDayOfWeek);
        LocalDate sunday = monday.plusDays(6);
        jdbc.update("delete from study_week_plans where user_id = ? and week = ?", userId, Date.valueOf(monday));
        jdbc.update("delete from study_week_slots where user_id = ? and day between ? and ?", userId, Date.valueOf(monday),
                Date.valueOf(sunday));
        jdbc.update("delete from study_lesson_pins where user_id = ? and day between ? and ?", userId, Date.valueOf(monday),
                Date.valueOf(sunday));
    }

    /** Semana automática vira plano com as aulas que a previsão mostra de hoje em diante. */
    private void ensurePlanned(UUID userId, LocalDate monday, LocalDate today) {
        Integer planned = jdbc.queryForObject("select count(*) from study_week_plans where user_id = ? and week = ?",
                Integer.class, userId, Date.valueOf(monday));
        if (planned != null && planned > 0) {
            return;
        }
        for (Day day : calendar.calendar(userId, monday, monday.plusDays(6))) {
            if (!day.date().isBefore(today)) {
                day.items().stream()
                        .filter(i -> i.kind() == Kind.LESSON)
                        .forEach(i -> insert(userId, i.subjectId(), day.date()));
            }
        }
        jdbc.update("delete from study_lesson_pins where user_id = ? and day between ? and ?", userId, Date.valueOf(monday),
                Date.valueOf(monday.plusDays(6)));
        markPlanned(userId, monday);
    }

    private void markPlanned(UUID userId, LocalDate monday) {
        jdbc.update("insert into study_week_plans (user_id, week) values (?, ?) on conflict do nothing", userId, Date.valueOf(monday));
    }

    private void insert(UUID userId, UUID subjectId, LocalDate day) {
        jdbc.update("insert into study_week_slots (user_id, subject_id, day) values (?, ?, ?) on conflict do nothing",
                userId, subjectId, Date.valueOf(day));
    }

    private boolean hasSlot(UUID userId, UUID subjectId, LocalDate day) {
        Integer n = jdbc.queryForObject("select count(*) from study_week_slots where user_id = ? and subject_id = ? and day = ?",
                Integer.class, userId, subjectId, Date.valueOf(day));
        return n != null && n > 0;
    }

    private List<Day> week(UUID userId, LocalDate monday) {
        return calendar.calendar(userId, monday, monday.plusDays(6));
    }

    private Subject requireSubject(UUID userId, UUID subjectId) {
        return subjects.findByIdAndUserId(subjectId, userId)
                .filter(s -> !s.isArchived())
                .orElseThrow(() -> new NotFoundException("Matéria não encontrada"));
    }

    /** Segunda da semana; a semana precisa ter algum dia de hoje em diante. */
    private static LocalDate editableWeek(LocalDate today, LocalDate anyDayOfWeek) {
        LocalDate monday = StudyCalendarService.mondayOf(anyDayOfWeek);
        if (monday.plusDays(6).isBefore(today)) {
            throw new InvalidRequestException("week", "só dá para planejar a semana de hoje ou as próximas");
        }
        return monday;
    }

    private static LocalDate editableDay(LocalDate today, LocalDate day) {
        if (day.isBefore(today)) {
            throw new InvalidRequestException("day", "só dá para planejar de hoje em diante");
        }
        return StudyCalendarService.mondayOf(day);
    }
}
