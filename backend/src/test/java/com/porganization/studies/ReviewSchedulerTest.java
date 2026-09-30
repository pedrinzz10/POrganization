package com.porganization.studies;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReviewSchedulerTest {

    // E07 T3 (CA3): metade da aula, arredondado para cima, no mínimo 5 minutos
    @ParameterizedTest(name = "aula de {0} min -> revisão de {1} min")
    @CsvSource({ "7, 5", "51, 26", "50, 25", "10, 5", "11, 6", "0, 5", "240, 120" })
    void duracaoDaRevisao(int aula, int revisao) {
        assertThat(ReviewScheduler.reviewMinutes(aula)).isEqualTo(revisao);
    }
}
