package com.porganization.commitments.recurrence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.porganization.common.InvalidRequestException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurrenceExpanderTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private static RecurrenceRule regra(Frequency freq, Integer interval, List<WeekDay> dias, String until, Integer count) {
        return new RecurrenceRule(freq, interval, dias, until == null ? null : d(until), count);
    }

    // C04 T1 (CA1)
    @Test
    void semanalNosDiasEscolhidos() {
        RecurrenceRule academia = regra(Frequency.WEEKLY, 1, List.of(WeekDay.MON, WeekDay.WED, WeekDay.FRI), null, null);

        assertThat(RecurrenceExpander.expand(d("2026-10-01"), academia, d("2026-10-05"), d("2026-10-11")))
                .containsExactly(d("2026-10-05"), d("2026-10-07"), d("2026-10-09"));
    }

    // C04 T2 (CA2)
    @Test
    void mensalNoDia31CaiNoUltimoDiaDosMesesMenores() {
        RecurrenceRule mensal = regra(Frequency.MONTHLY, 1, null, null, null);

        assertThat(RecurrenceExpander.expand(d("2026-10-31"), mensal, d("2026-10-01"), d("2027-02-28")))
                .containsExactly(d("2026-10-31"), d("2026-11-30"), d("2026-12-31"), d("2027-01-31"), d("2027-02-28"));
    }

    // C04 T3 (CA3)
    @Test
    void countEncerraASerie() {
        RecurrenceRule tresVezes = regra(Frequency.DAILY, 1, null, null, 3);

        assertThat(RecurrenceExpander.expand(d("2026-10-01"), tresVezes, d("2026-10-01"), d("2026-10-10")))
                .containsExactly(d("2026-10-01"), d("2026-10-02"), d("2026-10-03"));
    }

    // C04 T3 (CA3)
    @Test
    void semFimExpandeSoOIntervaloPedido() {
        RecurrenceRule diaria = regra(Frequency.DAILY, 1, null, null, null);

        assertThat(RecurrenceExpander.expand(d("2020-01-01"), diaria, d("2026-10-01"), d("2026-10-07"))).hasSize(7);
    }

    // C04 T3 (CA3)
    @Test
    void untilEncerraASerieInclusive() {
        RecurrenceRule ateDia3 = regra(Frequency.DAILY, 1, null, "2026-10-03", null);

        assertThat(RecurrenceExpander.expand(d("2026-10-01"), ateDia3, d("2026-09-01"), d("2026-10-31")))
                .containsExactly(d("2026-10-01"), d("2026-10-02"), d("2026-10-03"));
    }

    @Test
    void countContaAsOcorrenciasAnterioresAoIntervalo() {
        RecurrenceRule cincoVezes = regra(Frequency.DAILY, 1, null, null, 5);

        // começou dia 1: as ocorrências 1..5 são dias 1..5; na janela 4..10 sobram 4 e 5
        assertThat(RecurrenceExpander.expand(d("2026-10-01"), cincoVezes, d("2026-10-04"), d("2026-10-10")))
                .containsExactly(d("2026-10-04"), d("2026-10-05"));
    }

    @Test
    void semanalACadaDuasSemanas() {
        RecurrenceRule quinzenal = regra(Frequency.WEEKLY, 2, List.of(WeekDay.TUE), null, null);

        assertThat(RecurrenceExpander.expand(d("2026-10-06"), quinzenal, d("2026-10-01"), d("2026-11-10")))
                .containsExactly(d("2026-10-06"), d("2026-10-20"), d("2026-11-03"));
    }

    @Test
    void semanalSemDiasUsaODiaDoInicio() {
        RecurrenceRule terca = regra(Frequency.WEEKLY, 1, null, null, null);

        assertThat(RecurrenceExpander.expand(d("2026-10-06"), terca, d("2026-10-01"), d("2026-10-20")))
                .containsExactly(d("2026-10-06"), d("2026-10-13"), d("2026-10-20"));
    }

    @Test
    void semanalNaoGeraDatasAntesDoInicio() {
        // começa numa quinta; na primeira semana, a segunda e a quarta já passaram
        RecurrenceRule academia = regra(Frequency.WEEKLY, 1, List.of(WeekDay.MON, WeekDay.WED, WeekDay.FRI), null, null);

        assertThat(RecurrenceExpander.expand(d("2026-10-01"), academia, d("2026-09-28"), d("2026-10-04")))
                .containsExactly(d("2026-10-02"));
    }

    @Test
    void anualEm29DeFevereiroCaiEm28NosAnosComuns() {
        RecurrenceRule aniversario = regra(Frequency.YEARLY, 1, null, null, null);

        assertThat(RecurrenceExpander.expand(d("2024-02-29"), aniversario, d("2025-01-01"), d("2028-12-31")))
                .containsExactly(d("2025-02-28"), d("2026-02-28"), d("2027-02-28"), d("2028-02-29"));
    }

    @Test
    void mensalComIntervaloEComecoAntigoPulaDireto() {
        RecurrenceRule trimestral = regra(Frequency.MONTHLY, 3, null, null, null);

        assertThat(RecurrenceExpander.expand(d("2000-01-15"), trimestral, d("2026-10-01"), d("2027-03-31")))
                .containsExactly(d("2026-10-15"), d("2027-01-15"));
    }

    @Test
    void intervaloAntesDoInicioNaoTemOcorrencias() {
        RecurrenceRule diaria = regra(Frequency.DAILY, 1, null, null, null);

        assertThat(RecurrenceExpander.expand(d("2026-10-10"), diaria, d("2026-10-01"), d("2026-10-05"))).isEmpty();
    }

    @Test
    void validaARegra() {
        LocalDate inicio = d("2026-10-01");
        assertThatThrownBy(() -> RecurrenceExpander.validate(inicio, regra(null, 1, null, null, null)))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("frequência");
        assertThatThrownBy(() -> RecurrenceExpander.validate(inicio, regra(Frequency.DAILY, 1, null, "2026-12-01", 3)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> RecurrenceExpander.validate(inicio, regra(Frequency.DAILY, 0, null, null, null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> RecurrenceExpander.validate(inicio, regra(Frequency.DAILY, 1, List.of(WeekDay.MON), null, null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> RecurrenceExpander.validate(inicio, regra(Frequency.DAILY, 1, null, "2026-09-30", null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> RecurrenceExpander.validate(inicio, regra(Frequency.DAILY, 1, null, null, 1001)))
                .isInstanceOf(InvalidRequestException.class);
    }
}
