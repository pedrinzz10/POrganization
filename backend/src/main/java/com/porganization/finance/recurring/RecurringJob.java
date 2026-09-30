package com.porganization.finance.recurring;

import com.porganization.settings.UserSettingsService;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Job diário: gera o mês corrente (no fuso de cada usuário) para quem tem modelo ativo. */
@Component
public class RecurringJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringJob.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final RecurringTransactionRepository recurring;
    private final RecurringGenerator generator;
    private final UserSettingsService userSettings;
    private final Clock clock;

    public RecurringJob(RecurringTransactionRepository recurring, RecurringGenerator generator,
            UserSettingsService userSettings, Clock clock) {
        this.recurring = recurring;
        this.generator = generator;
        this.userSettings = userSettings;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.jobs.recurring-cron:0 10 3 * * *}", zone = "America/Sao_Paulo")
    public void run() {
        int created = 0;
        // Usuário em fuso adiantado pode já ter virado o mês: o seguinte entra no próximo dia
        for (UUID userId : recurring.usersActiveIn(YearMonth.now(clock.withZone(DEFAULT_ZONE)).atDay(1))) {
            try {
                created += generator.generate(userId, YearMonth.now(clock.withZone(userSettings.zoneOf(userId))));
            } catch (RuntimeException e) {
                log.error("Falha ao gerar recorrentes do usuário {}", userId, e);
            }
        }
        log.info("Recorrentes: {} transações geradas", created);
    }
}
