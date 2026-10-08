package com.porganization.studies;

import com.porganization.security.CurrentUser;
import com.porganization.studies.StudyCalendarPlanner.Day;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Plano da semana de estudos (E15). "week" é qualquer dia da semana; as respostas trazem a semana. */
@RestController
@RequestMapping("/api/study/week")
public class StudyWeekPlanController {

    private final StudyWeekPlanService service;

    public StudyWeekPlanController(StudyWeekPlanService service) {
        this.service = service;
    }

    public record SlotRequest(@NotNull UUID subjectId, @NotNull LocalDate day) {
    }

    /** "Gerar semana": sorteia os dias das aulas da meta semanal. */
    @PostMapping("/generate")
    public List<Day> generate(@CurrentUser UUID userId, @RequestParam LocalDate week) {
        return service.generate(userId, week);
    }

    @PostMapping("/slots")
    public List<Day> add(@CurrentUser UUID userId, @Valid @RequestBody SlotRequest request) {
        return service.add(userId, request.subjectId(), request.day());
    }

    @DeleteMapping("/slots")
    public List<Day> remove(@CurrentUser UUID userId, @RequestParam UUID subjectId, @RequestParam LocalDate day) {
        return service.remove(userId, subjectId, day);
    }

    /** Volta a semana à previsão automática. */
    @DeleteMapping
    public ResponseEntity<Void> clear(@CurrentUser UUID userId, @RequestParam LocalDate week) {
        service.clear(userId, week);
        return ResponseEntity.noContent().build();
    }
}
