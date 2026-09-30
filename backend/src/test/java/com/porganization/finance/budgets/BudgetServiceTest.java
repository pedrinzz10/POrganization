package com.porganization.finance.budgets;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.finance.budgets.BudgetDtos.BudgetLevel;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BudgetServiceTest {

    // F09 T1 (CA1)
    @ParameterizedTest
    @CsvSource({
            "500.00, 0.00, 0.00, OK",
            "500.00, 399.99, 79.99, OK",
            "500.00, 400.00, 80.00, ATENCAO",
            "500.00, 499.99, 99.99, ATENCAO",
            "500.00, 500.00, 100.00, ESTOURADO",
            "500.00, 750.00, 150.00, ESTOURADO"
    })
    void nivelEPercentual(String orcado, String gasto, String percentual, BudgetLevel nivel) {
        BigDecimal amount = new BigDecimal(orcado);
        BigDecimal spent = new BigDecimal(gasto);
        assertThat(BudgetService.percent(amount, spent)).isEqualByComparingTo(percentual);
        assertThat(BudgetService.level(amount, spent)).isEqualTo(nivel);
    }
}
