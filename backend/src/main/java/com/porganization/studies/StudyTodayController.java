package com.porganization.studies;

import com.porganization.security.CurrentUser;
import com.porganization.studies.dto.StudyTodayResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study/today")
public class StudyTodayController {

    private final StudyTodayService service;

    public StudyTodayController(StudyTodayService service) {
        this.service = service;
    }

    /** O que estudar hoje: revisões vencidas primeiro, depois as aulas pela prioridade. */
    @GetMapping
    public StudyTodayResponse today(@CurrentUser UUID userId) {
        return service.today(userId);
    }
}
