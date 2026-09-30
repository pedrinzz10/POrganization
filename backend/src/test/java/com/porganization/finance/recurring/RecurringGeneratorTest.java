package com.porganization.finance.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RecurringGeneratorTest {

    // F08 T2 (CA2)
    @ParameterizedTest
    @CsvSource({
            "31, 2027-02, 2027-02-28",
            "31, 2028-02, 2028-02-29",
            "31, 2026-04, 2026-04-30",
            "31, 2026-10, 2026-10-31",
            "10, 2026-02, 2026-02-10"
    })
    void diaQueNaoExisteNoMesCaiNoUltimoDia(int dia, String mes, String esperado) {
        assertThat(RecurringGenerator.dateFor(dia, YearMonth.parse(mes))).isEqualTo(LocalDate.parse(esperado));
    }
}
