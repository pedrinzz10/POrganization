package com.porganization.studies.fsrs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FsrsTest {

    private final Fsrs fsrs = new Fsrs(FsrsParameters.defaults());
    private final LocalDate inicio = LocalDate.of(2026, 10, 1);

    // E05 T2 (CA2)
    @Test
    void facilAgendaMaisLongeQueOkQueDificil() {
        FsrsCard card = fsrs.review(FsrsCard.newCard(), ReviewGrade.OK, inicio);
        card = fsrs.review(card, ReviewGrade.OK, card.due());

        Map<ReviewGrade, FsrsCard> opcoes = fsrs.preview(card, card.due());

        int dificil = opcoes.get(ReviewGrade.DIFICIL).scheduledDays();
        int ok = opcoes.get(ReviewGrade.OK).scheduledDays();
        int facil = opcoes.get(ReviewGrade.FACIL).scheduledDays();
        assertThat(facil).isGreaterThan(ok);
        assertThat(ok).isGreaterThan(dificil);
    }

    @Test
    void cartaoNovoTambemRespeitaAOrdem() {
        Map<ReviewGrade, FsrsCard> opcoes = fsrs.preview(FsrsCard.newCard(), inicio);

        assertThat(opcoes.get(ReviewGrade.FACIL).scheduledDays())
                .isGreaterThan(opcoes.get(ReviewGrade.OK).scheduledDays());
        assertThat(opcoes.get(ReviewGrade.OK).scheduledDays())
                .isGreaterThan(opcoes.get(ReviewGrade.DIFICIL).scheduledDays());
    }

    // E05 T3 (CA3)
    @Test
    void intervaloSempreEntreUmDiaEOMaximo() {
        FsrsCard card = FsrsCard.newCard();
        LocalDate dia = inicio;
        for (int i = 0; i < 30; i++) {
            card = fsrs.review(card, ReviewGrade.FACIL, dia);
            assertThat(card.scheduledDays()).as("revisão %d", i + 1).isBetween(1, 365);
            dia = card.due();
        }
    }

    @Test
    void maximoConfiguravel() {
        Fsrs curto = new Fsrs(FsrsParameters.defaults().withMaximumInterval(30));
        FsrsCard card = FsrsCard.newCard();
        LocalDate dia = inicio;
        for (int i = 0; i < 10; i++) {
            card = curto.review(card, ReviewGrade.FACIL, dia);
            assertThat(card.scheduledDays()).isBetween(1, 30);
            dia = card.due();
        }
    }

    @Test
    void revisaoConsecutivaSemDificuldadeFicaNaFaixa() {
        FsrsCard card = FsrsCard.newCard();
        LocalDate dia = inicio;
        for (int i = 0; i < 20; i++) {
            card = fsrs.review(card, ReviewGrade.DIFICIL, dia);
            assertThat(card.difficulty()).isBetween(1.0, 10.0);
            assertThat(card.scheduledDays()).isGreaterThanOrEqualTo(1);
            dia = card.due();
        }
    }
}
