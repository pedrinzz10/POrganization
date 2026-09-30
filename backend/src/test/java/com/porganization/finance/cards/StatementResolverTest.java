package com.porganization.finance.cards;

import static org.assertj.core.api.Assertions.assertThat;

import com.porganization.finance.cards.StatementResolver.StatementPeriod;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatementResolverTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    // F05 T1 (CA1)
    @Test
    void compraNoDiaDoFechamentoVaiParaAProximaFatura() {
        StatementPeriod antes = StatementResolver.resolve(5, 12, d("2026-10-04"));
        StatementPeriod noFechamento = StatementResolver.resolve(5, 12, d("2026-10-05"));

        assertThat(antes.dueDate()).isEqualTo(d("2026-10-12"));
        assertThat(antes.closingDate()).isEqualTo(d("2026-10-05"));
        assertThat(antes.referenceMonth()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(noFechamento.dueDate()).isEqualTo(d("2026-11-12"));
        assertThat(noFechamento.closingDate()).isEqualTo(d("2026-11-05"));
    }

    // F05 T2 (CA2)
    @Test
    void fechamentoEmDiaInexistenteUsaOUltimoDiaDoMes() {
        assertThat(StatementResolver.closingDate(31, YearMonth.of(2027, 2))).isEqualTo(d("2027-02-28"));
        assertThat(StatementResolver.closingDate(31, YearMonth.of(2028, 2))).isEqualTo(d("2028-02-29"));
        assertThat(StatementResolver.closingDate(31, YearMonth.of(2026, 4))).isEqualTo(d("2026-04-30"));

        // compra em 28/02/2027 com fechamento "31": o fechamento de fevereiro é hoje, vai para março
        StatementPeriod p = StatementResolver.resolve(31, 10, d("2027-02-28"));
        assertThat(p.closingDate()).isEqualTo(d("2027-03-31"));
        assertThat(p.dueDate()).isEqualTo(d("2027-04-10"));
    }

    @ParameterizedTest(name = "fecha {0}, vence {1}: compra {2} -> fecha {3}, vence {4}")
    @CsvSource({
        // vencimento depois do fechamento: vence no mesmo mês do fechamento
        "5, 12, 2026-12-10, 2027-01-05, 2027-01-12",
        // vencimento antes (ou no dia) do fechamento: vence no mês seguinte ao fechamento
        "25, 5, 2026-10-20, 2026-10-25, 2026-11-05",
        "25, 5, 2026-10-25, 2026-11-25, 2026-12-05",
        "10, 10, 2026-10-01, 2026-10-10, 2026-11-10",
        // vencimento no dia 31 em mês curto
        "20, 31, 2026-10-21, 2026-11-20, 2026-11-30",
    })
    void combinacoesDeFechamentoEVencimento(int fecha, int vence, String compra, String fechamento, String vencimento) {
        StatementPeriod p = StatementResolver.resolve(fecha, vence, d(compra));
        assertThat(p.closingDate()).isEqualTo(d(fechamento));
        assertThat(p.dueDate()).isEqualTo(d(vencimento));
        assertThat(p.referenceMonth()).isEqualTo(YearMonth.from(d(vencimento)));
    }
}
