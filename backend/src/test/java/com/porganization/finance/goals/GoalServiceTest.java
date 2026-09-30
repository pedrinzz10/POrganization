package com.porganization.finance.goals;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GoalServiceTest {

    private static final LocalDate HOJE = LocalDate.parse("2026-10-15");

    // F10 T2 (CA2)
    @ParameterizedTest
    @CsvSource({
            "1000.00, 2027-01-10, 333.34",   // out → jan = 3 meses, arredonda para cima
            "1000.00, 2026-10-31, 1000.00",  // prazo neste mês = 1 mês
            "1000.00, 2026-10-14, 1000.00",  // prazo vencido = tudo o que falta
            "1000.00, 2025-01-01, 1000.00",
            "1200.00, 2027-10-01, 100.00",   // 12 meses
            "0.00, 2027-01-10, 0.00"         // já batida
    })
    void aporteMensalNecessario(String falta, String prazo, String esperado) {
        assertThat(GoalService.monthlyNeeded(new BigDecimal(falta), HOJE, LocalDate.parse(prazo))).isEqualByComparingTo(esperado);
    }

    @Test
    void semPrazoNaoHaAporteSugerido() {
        assertThat(GoalService.monthlyNeeded(new BigDecimal("1000.00"), HOJE, null)).isNull();
    }

    @Test
    void progressoTruncado() {
        assertThat(GoalService.progress(new BigDecimal("6000.00"), new BigDecimal("1500.00"))).isEqualByComparingTo("25.00");
        assertThat(GoalService.progress(new BigDecimal("3.00"), new BigDecimal("2.00"))).isEqualByComparingTo("66.66");
    }
}
