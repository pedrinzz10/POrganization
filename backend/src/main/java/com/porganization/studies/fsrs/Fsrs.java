package com.porganization.studies.fsrs;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

/**
 * Algoritmo FSRS-6 (Free Spaced Repetition Scheduler), portado do ts-fsrs 5.4.2
 * (src/algorithm.ts e src/impl/long_term_scheduler.ts), com o agendador de longo prazo:
 * intervalos em dias, sem passos de minutos, e sem fuzz (resultado determinístico).
 *
 * <p>Os arredondamentos para 8 casas acontecem nos mesmos pontos da referência, para os
 * resultados baterem (FsrsReferenceTest). A única diferença intencional: o teto de intervalo
 * máximo é aplicado por último, depois de forçar hard < good < easy (a referência pode passar
 * do máximo nesse ajuste).
 */
public final class Fsrs {

    private static final double S_MIN = 0.001;
    private static final double S_MAX = 36500.0;
    private static final int AGAIN = 1;
    private static final int HARD = 2;
    private static final int GOOD = 3;
    private static final int EASY = 4;

    private final double[] w;
    private final int maximumInterval;
    private final double decay;
    private final double factor;
    private final double intervalModifier;

    public Fsrs(FsrsParameters parameters) {
        this.w = parameters.w();
        this.maximumInterval = parameters.maximumInterval();
        this.decay = -w[20];
        this.factor = roundTo(Math.exp(Math.log(0.9) / decay) - 1.0, 8);
        this.intervalModifier = roundTo((Math.pow(parameters.requestRetention(), 1 / decay) - 1) / factor, 8);
    }

    /** Aplica a nota da revisão feita em "reviewDate" e devolve o novo estado, já agendado. */
    public FsrsCard review(FsrsCard card, ReviewGrade grade, LocalDate reviewDate) {
        return preview(card, reviewDate).get(grade);
    }

    /** Como ficaria o item com cada nota possível (útil para mostrar "Difícil: 3 dias" etc.). */
    public Map<ReviewGrade, FsrsCard> preview(FsrsCard card, LocalDate reviewDate) {
        boolean isNew = card.state() == FsrsCard.State.NEW;
        int elapsed = isNew || card.lastReview() == null
                ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(card.lastReview(), reviewDate));
        Double retrievability = isNew ? null : forgettingCurve(elapsed, card.stability());

        double[][] next = new double[5][];
        for (int g = AGAIN; g <= EASY; g++) {
            next[g] = nextState(isNew ? null : card, elapsed, g, retrievability);
        }

        // Intervalos na ordem da referência: again <= hard < good < easy
        int again = nextInterval(next[AGAIN][1]);
        int hard = nextInterval(next[HARD][1]);
        int good = nextInterval(next[GOOD][1]);
        int easy = nextInterval(next[EASY][1]);
        again = Math.min(again, hard);
        hard = Math.max(hard, again + 1);
        good = Math.max(good, hard + 1);
        easy = Math.max(easy, good + 1);

        Map<ReviewGrade, FsrsCard> result = new EnumMap<>(ReviewGrade.class);
        result.put(ReviewGrade.DIFICIL, scheduled(card, next[HARD], hard, reviewDate));
        result.put(ReviewGrade.OK, scheduled(card, next[GOOD], good, reviewDate));
        result.put(ReviewGrade.FACIL, scheduled(card, next[EASY], easy, reviewDate));
        return result;
    }

    private FsrsCard scheduled(FsrsCard card, double[] state, int interval, LocalDate reviewDate) {
        int days = Math.min(interval, maximumInterval); // teto aplicado por último (CA3)
        return new FsrsCard(FsrsCard.State.REVIEW, state[1], state[0], card.reps() + 1, card.lapses(), reviewDate,
                reviewDate.plusDays(days), days);
    }

    /** {difficulty, stability} depois da nota g; memory null = primeira revisão. */
    private double[] nextState(FsrsCard memory, int elapsed, int g, Double retrievability) {
        if (memory == null) {
            return new double[] { clamp(initDifficulty(g), 1, 10), initStability(g) };
        }
        double d = memory.difficulty();
        double s = memory.stability();
        double r = retrievability != null ? retrievability : forgettingCurve(elapsed, s);
        double newS;
        if (g == AGAIN) {
            double afterFail = nextForgetStability(d, s, r);
            // sem passos de curto prazo, w17/w18 não entram: o mínimo é a própria stability
            newS = clamp(roundTo(s, 8), S_MIN, afterFail);
        } else {
            newS = nextRecallStability(d, s, r, g);
        }
        return new double[] { nextDifficulty(d, g), newS };
    }

    private double forgettingCurve(double elapsedDays, double stability) {
        return roundTo(Math.pow(1 + factor * elapsedDays / stability, decay), 8);
    }

    private int nextInterval(double stability) {
        return (int) Math.min(Math.max(1, Math.round(stability * intervalModifier)), maximumInterval);
    }

    private double initStability(int g) {
        return Math.max(w[g - 1], 0.1);
    }

    private double initDifficulty(int g) {
        return roundTo(w[4] - Math.exp((g - 1) * w[5]) + 1, 8);
    }

    private double nextDifficulty(double d, int g) {
        double deltaD = -w[6] * (g - 3);
        double nextD = d + linearDamping(deltaD, d);
        return clamp(meanReversion(initDifficulty(EASY), nextD), 1, 10);
    }

    private double linearDamping(double deltaD, double oldD) {
        return roundTo(deltaD * (10 - oldD) / 9, 8);
    }

    private double meanReversion(double init, double current) {
        return roundTo(w[7] * init + (1 - w[7]) * current, 8);
    }

    private double nextRecallStability(double d, double s, double r, int g) {
        double hardPenalty = g == HARD ? w[15] : 1;
        double easyBonus = g == EASY ? w[16] : 1;
        return roundTo(clamp(s * (1 + Math.exp(w[8]) * (11 - d) * Math.pow(s, -w[9])
                * (Math.exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus), S_MIN, S_MAX), 8);
    }

    private double nextForgetStability(double d, double s, double r) {
        return roundTo(clamp(w[11] * Math.pow(d, -w[12]) * (Math.pow(s + 1, w[13]) - 1)
                * Math.exp((1 - r) * w[14]), S_MIN, S_MAX), 8);
    }

    private static double roundTo(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }
}
