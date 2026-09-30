package com.porganization.finance.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HolidaysTest {

    // F17 T1 (CA1)
    @ParameterizedTest
    @CsvSource({ "2024, 2024-03-31", "2025, 2025-04-20", "2026, 2026-04-05", "2027, 2027-03-28", "2038, 2038-04-25" })
    void pascoa(int ano, LocalDate esperada) {
        assertThat(Holidays.easter(ano)).isEqualTo(esperada);
    }

    // F17 T1 (CA1)
    @Test
    void moveisDe2026() {
        assertThat(Holidays.of(2026)).contains(
                LocalDate.parse("2026-02-16"), LocalDate.parse("2026-02-17"), // Carnaval
                LocalDate.parse("2026-04-03"),                                // Sexta-feira Santa
                LocalDate.parse("2026-06-04"));                               // Corpus Christi
        // Quarta de cinzas não é feriado bancário (expediente começa mais tarde, mas abre)
        assertThat(Holidays.isHoliday(LocalDate.parse("2026-02-18"))).isFalse();
    }

    @Test
    void fixosNacionais() {
        assertThat(Holidays.of(2026)).contains(
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-21"), LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-09-07"), LocalDate.parse("2026-10-12"), LocalDate.parse("2026-11-02"),
                LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-20"), LocalDate.parse("2026-12-25"));
        assertThat(Holidays.of(2026)).hasSize(13);
    }
}
