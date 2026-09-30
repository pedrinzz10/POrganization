package com.porganization.finance.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.porganization.finance.calendar.BusinessCalendar.Adjustment;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BusinessCalendarTest {

    // F17 T2 (CA2)
    @ParameterizedTest
    @CsvSource({
            "2026-10, 5, 2026-10-07",  // qui 1, sex 2, seg 5, ter 6, qua 7
            "2026-11, 5, 2026-11-09",  // dom 1, Finados seg 2: ter 3 ... seg 9
            "2026-02, 1, 2026-02-02",  // dom 1
            "2026-02, 11, 2026-02-18", // pula o Carnaval (16 e 17)
            "2026-04, 1, 2026-04-01",
            "2026-04, 3, 2026-04-06"   // qua 1, qui 2, Sexta-feira Santa 3, seg 6
    })
    void enesimoDiaUtil(YearMonth mes, int n, LocalDate esperado) {
        assertThat(BusinessCalendar.nthBusinessDay(mes, n)).isEqualTo(esperado);
    }

    // F17 T2 (CA2)
    @Test
    void ultimoDiaUtilEErroQuandoOMesNaoTemTantosDiasUteis() {
        assertThat(BusinessCalendar.lastBusinessDay(YearMonth.of(2026, 2))).isEqualTo(LocalDate.parse("2026-02-27"));
        assertThat(BusinessCalendar.lastBusinessDay(YearMonth.of(2026, 12))).isEqualTo(LocalDate.parse("2026-12-31"));
        // Fevereiro/2026: 20 dias de semana, menos 2 de Carnaval
        assertThat(BusinessCalendar.nthBusinessDay(YearMonth.of(2026, 2), 18)).isEqualTo(LocalDate.parse("2026-02-27"));
        assertThatThrownBy(() -> BusinessCalendar.nthBusinessDay(YearMonth.of(2026, 2), 25))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("18 dias úteis");
        assertThatThrownBy(() -> BusinessCalendar.nthBusinessDay(YearMonth.of(2026, 2), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // F17 T3 (CA3)
    @Test
    void ajusteDeDataNaoUtil() {
        LocalDate domingo = LocalDate.parse("2026-09-20");
        assertThat(BusinessCalendar.adjust(domingo, Adjustment.ANTICIPATE)).isEqualTo(LocalDate.parse("2026-09-18"));
        assertThat(BusinessCalendar.adjust(domingo, Adjustment.POSTPONE)).isEqualTo(LocalDate.parse("2026-09-21"));
        assertThat(BusinessCalendar.adjust(domingo, Adjustment.KEEP)).isEqualTo(domingo);

        LocalDate terca = LocalDate.parse("2026-09-22");
        for (Adjustment a : Adjustment.values()) {
            assertThat(BusinessCalendar.adjust(terca, a)).isEqualTo(terca);
        }
    }

    @Test
    void ajusteAtravessaFeriadoColadoNoFimDeSemana() {
        // Sábado 04/04/2026: antecipar pula a Sexta-feira Santa (03/04) e vai para quinta 02/04
        assertThat(BusinessCalendar.adjust(LocalDate.parse("2026-04-04"), Adjustment.ANTICIPATE))
                .isEqualTo(LocalDate.parse("2026-04-02"));
        // Sábado 14/02/2026: adiar pula o Carnaval (16 e 17) e vai para quarta 18/02
        assertThat(BusinessCalendar.adjust(LocalDate.parse("2026-02-14"), Adjustment.POSTPONE))
                .isEqualTo(LocalDate.parse("2026-02-18"));
    }
}
