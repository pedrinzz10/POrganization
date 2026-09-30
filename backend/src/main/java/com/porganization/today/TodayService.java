package com.porganization.today;

import com.porganization.commitments.CommitmentService;
import com.porganization.finance.recurring.RecurringGenerator;
import com.porganization.finance.today.FinanceTodayService;
import com.porganization.settings.UserSettingsService;
import com.porganization.studies.StudyTodayService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Agrega as seções da tela Hoje (compromissos, estudos e finanças). "Hoje" é o dia no fuso do
 * usuário. Sem transação própria: primeiro gera os fixos da janela de vencimentos (escrita), depois
 * cada seção lê na sua transação.
 */
@Service
public class TodayService {

    private final Clock clock;
    private final UserSettingsService userSettings;
    private final CommitmentService commitments;
    private final StudyTodayService studies;
    private final FinanceTodayService finance;
    private final RecurringGenerator recurring;

    public TodayService(Clock clock, UserSettingsService userSettings, CommitmentService commitments,
            StudyTodayService studies, FinanceTodayService finance, RecurringGenerator recurring) {
        this.clock = clock;
        this.userSettings = userSettings;
        this.commitments = commitments;
        this.studies = studies;
        this.finance = finance;
        this.recurring = recurring;
    }

    public TodayResponse today(UUID userId) {
        ZoneId zone = userSettings.zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        // Os fixos que vencem na janela precisam existir; ela pode atravessar a virada do mês
        YearMonth month = YearMonth.from(today);
        recurring.generate(userId, month);
        if (!YearMonth.from(today.plusDays(FinanceTodayService.DUE_WINDOW_DAYS)).equals(month)) {
            recurring.generate(userId, month.plusMonths(1));
        }
        return new TodayResponse(today, zone.getId(), commitments.findInRange(userId, today, today),
                studies.plan(userId, today, zone), finance.of(userId, today));
    }
}
