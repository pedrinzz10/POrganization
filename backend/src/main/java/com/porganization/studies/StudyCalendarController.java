package com.porganization.studies;

import com.porganization.security.CurrentUser;
import com.porganization.studies.StudyCalendarPlanner.Day;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study/calendar")
public class StudyCalendarController {

    private final StudyCalendarService service;

    public StudyCalendarController(StudyCalendarService service) {
        this.service = service;
    }

    /**
     * Agenda de estudos dia a dia entre from e to (inclusivos): o que foi estudado até hoje, as
     * revisões agendadas e as aulas da meta semanal espalhadas pelos dias que faltam.
     */
    @GetMapping
    public List<Day> calendar(@CurrentUser UUID userId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.calendar(userId, from, to);
    }
}
