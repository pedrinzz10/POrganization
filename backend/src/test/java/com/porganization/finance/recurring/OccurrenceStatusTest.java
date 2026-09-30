package com.porganization.finance.recurring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OccurrenceStatusTest {

    private static final LocalDate DIA_07 = LocalDate.parse("2026-10-07");

    // F18 T2 (CA2)
    @Test
    void estadoSaiDaDataEDoQueFoiFeito() {
        assertThat(OccurrenceStatus.of(false, DIA_07, DIA_07, LocalDate.parse("2026-10-06"))).isEqualTo(OccurrenceStatus.EXPECTED);
        assertThat(OccurrenceStatus.of(false, DIA_07, DIA_07, DIA_07)).isEqualTo(OccurrenceStatus.TO_CONFIRM);
        assertThat(OccurrenceStatus.of(false, DIA_07, DIA_07, LocalDate.parse("2026-10-08"))).isEqualTo(OccurrenceStatus.OVERDUE);
        for (String hoje : new String[] {"2026-10-01", "2026-10-07", "2026-12-31"}) {
            assertThat(OccurrenceStatus.of(true, DIA_07, DIA_07, LocalDate.parse(hoje))).isEqualTo(OccurrenceStatus.CONFIRMED);
        }
    }

    @Test
    void remarcadaFicaRemarcadaAteANovaDataChegar() {
        LocalDate novaData = LocalDate.parse("2026-10-10");

        assertThat(OccurrenceStatus.of(false, novaData, DIA_07, LocalDate.parse("2026-10-08"))).isEqualTo(OccurrenceStatus.RESCHEDULED);
        assertThat(OccurrenceStatus.of(false, novaData, DIA_07, novaData)).isEqualTo(OccurrenceStatus.TO_CONFIRM);
        assertThat(OccurrenceStatus.of(false, novaData, DIA_07, LocalDate.parse("2026-10-11"))).isEqualTo(OccurrenceStatus.OVERDUE);
    }
}
