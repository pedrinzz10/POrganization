package com.porganization.studies;

import com.porganization.security.CurrentUser;
import com.porganization.studies.StudyCalendarPlanner.Day;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study/calendar")
public class StudyCalendarController {

    private final StudyCalendarService service;
    private final StudyWeekPlanService weekPlans;

    public StudyCalendarController(StudyCalendarService service, StudyWeekPlanService weekPlans) {
        this.service = service;
        this.weekPlans = weekPlans;
    }

    /**
     * Agenda de estudos dia a dia entre from e to (inclusivos): o que foi estudado até hoje, as
     * revisões agendadas e as aulas da semana (do plano, ou a previsão automática da meta semanal).
     */
    @GetMapping
    public List<Day> calendar(@CurrentUser UUID userId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.calendar(userId, from, to);
    }

    public record MoveRequest(@NotNull UUID subjectId, @NotNull LocalDate from, @NotNull LocalDate to) {
    }

    /** Arrastar na agenda: a aula da matéria vai de "from" para "to" (mesma semana, de hoje em diante). */
    @PostMapping("/moves")
    public List<Day> move(@CurrentUser UUID userId, @Valid @RequestBody MoveRequest request) {
        return weekPlans.move(userId, request.subjectId(), request.from(), request.to());
    }
}
