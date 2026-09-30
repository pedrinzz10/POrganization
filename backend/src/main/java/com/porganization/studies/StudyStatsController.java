package com.porganization.studies;

import com.porganization.security.CurrentUser;
import com.porganization.studies.dto.StudyStatsResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/study/stats")
public class StudyStatsController {

    private final StudyStatsService service;

    public StudyStatsController(StudyStatsService service) {
        this.service = service;
    }

    @GetMapping
    public StudyStatsResponse stats(@CurrentUser UUID userId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return service.stats(userId, from, to);
    }
}
