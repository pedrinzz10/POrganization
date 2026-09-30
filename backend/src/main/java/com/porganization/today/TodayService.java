package com.porganization.today;

import com.porganization.commitments.CommitmentService;
import com.porganization.settings.UserSettingsService;
import com.porganization.studies.StudyTodayService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Agrega as seções da tela Hoje (compromissos e estudos). "Hoje" é o dia no fuso do usuário. */
@Service
public class TodayService {

    private final Clock clock;
    private final UserSettingsService userSettings;
    private final CommitmentService commitments;
    private final StudyTodayService studies;

    public TodayService(Clock clock, UserSettingsService userSettings, CommitmentService commitments,
            StudyTodayService studies) {
        this.clock = clock;
        this.userSettings = userSettings;
        this.commitments = commitments;
        this.studies = studies;
    }

    @Transactional(readOnly = true)
    public TodayResponse today(UUID userId) {
        ZoneId zone = userSettings.zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        return new TodayResponse(today, zone.getId(), commitments.findInRange(userId, today, today),
                studies.plan(userId, today, zone));
    }
}
