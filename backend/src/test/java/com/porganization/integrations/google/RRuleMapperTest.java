package com.porganization.integrations.google;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.commitments.recurrence.Frequency;
import com.porganization.commitments.recurrence.RecurrenceRule;
import com.porganization.commitments.recurrence.WeekDay;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RRuleMapperTest {

    // I07 T2 (CA2)
    @Test
    void semanalSegundaEQuartaAteOFimDoAno() {
        RecurrenceRule regra = new RecurrenceRule(Frequency.WEEKLY, 1, List.of(WeekDay.WED, WeekDay.MON), LocalDate.parse("2026-12-31"), null);

        assertThat(RRuleMapper.toRRule(regra)).isEqualTo("RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261231T235959Z");
    }

    @Test
    void diarioCincoVezes() {
        assertThat(RRuleMapper.toRRule(new RecurrenceRule(Frequency.DAILY, null, null, null, 5)))
                .isEqualTo("RRULE:FREQ=DAILY;COUNT=5");
    }

    @Test
    void mensalSemFimEComIntervalo() {
        assertThat(RRuleMapper.toRRule(new RecurrenceRule(Frequency.MONTHLY, 1, null, null, null))).isEqualTo("RRULE:FREQ=MONTHLY");
        assertThat(RRuleMapper.toRRule(new RecurrenceRule(Frequency.MONTHLY, 2, null, null, 6)))
                .isEqualTo("RRULE:FREQ=MONTHLY;INTERVAL=2;COUNT=6");
    }

    @Test
    void diaTodoUsaUntilComoData() {
        assertThat(RRuleMapper.toRRule(new RecurrenceRule(Frequency.YEARLY, null, null, LocalDate.parse("2030-01-01"), null), true))
                .isEqualTo("RRULE:FREQ=YEARLY;UNTIL=20300101");
    }

    @Test
    void diasCanceladosViramExdate() {
        List<LocalDate> dias = List.of(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-12"));

        assertThat(RRuleMapper.toExDate(dias, LocalTime.of(7, 0), "America/Sao_Paulo"))
                .isEqualTo("EXDATE;TZID=America/Sao_Paulo:20261005T070000,20261012T070000");
        assertThat(RRuleMapper.toExDate(dias, null, "America/Sao_Paulo")).isEqualTo("EXDATE;VALUE=DATE:20261005,20261012");
    }

    // I08: caminho inverso (importação)
    @Test
    void regraDoGoogleQueCabeNoAppVoltaComoRecurrenceRule() {
        assertThat(RRuleMapper.fromRRule(List.of("RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261231T235959Z")))
                .contains(new RecurrenceRule(Frequency.WEEKLY, null, List.of(WeekDay.MON, WeekDay.WED), LocalDate.parse("2026-12-31"), null));
        assertThat(RRuleMapper.fromRRule(List.of("EXDATE;VALUE=DATE:20261005", "RRULE:FREQ=DAILY;INTERVAL=2;COUNT=5")))
                .contains(new RecurrenceRule(Frequency.DAILY, 2, null, null, 5));
    }

    @Test
    void regraMaisRicaQueOAppViraEventoUnico() {
        assertThat(RRuleMapper.fromRRule(List.of("RRULE:FREQ=MONTHLY;BYDAY=2MO"))).isEmpty();
        assertThat(RRuleMapper.fromRRule(List.of("RRULE:FREQ=MONTHLY;BYMONTHDAY=15"))).isEmpty();
        assertThat(RRuleMapper.fromRRule(List.of())).isEmpty();
    }
}
