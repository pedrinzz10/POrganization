package com.porganization.finance.cards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InstallmentCalculatorTest {

    // F06 T1 (CA1)
    @Test
    void cemEmTresVezesComORestoNaPrimeira() {
        assertThat(InstallmentCalculator.split(new BigDecimal("100.00"), 3))
                .containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
    }

    // F06 T1 (CA1): a soma é sempre exatamente o total
    @ParameterizedTest(name = "{0} em {1}x")
    @CsvSource({ "0.10, 3", "999.99, 12", "100.00, 3", "1234.56, 7", "0.48, 48", "50.00, 1", "10000.01, 48" })
    void somaExata(String total, int parcelas) {
        List<BigDecimal> valores = InstallmentCalculator.split(new BigDecimal(total), parcelas);

        assertThat(valores).hasSize(parcelas);
        assertThat(valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(total);
        assertThat(valores).allSatisfy(v -> assertThat(v.scale()).isEqualTo(2));
        // a primeira é a maior; as demais são iguais entre si
        assertThat(valores.subList(1, valores.size())).allSatisfy(v -> assertThat(v).isEqualByComparingTo(valores.getLast()));
        assertThat(valores.getFirst()).isGreaterThanOrEqualTo(valores.getLast());
    }

    @Test
    void zeroVirgulaDezEmTres() {
        assertThat(InstallmentCalculator.split(new BigDecimal("0.10"), 3))
                .containsExactly(new BigDecimal("0.04"), new BigDecimal("0.03"), new BigDecimal("0.03"));
    }

    @Test
    void valorMenorQueUmCentavoPorParcelaNaoPode() {
        assertThatThrownBy(() -> InstallmentCalculator.split(new BigDecimal("0.02"), 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentCalculator.split(new BigDecimal("10.00"), 49))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
