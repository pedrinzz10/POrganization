package com.porganization.studies.fsrs;

import java.time.LocalDate;

/**
 * Estado de memória de um item revisado (uma aula). Imutável: cada revisão devolve um novo.
 *
 * @param state         NEW até a primeira revisão; REVIEW depois (agendador em dias)
 * @param stability     dias até a chance de lembrar cair para 90%
 * @param difficulty    de 1 (fácil) a 10 (difícil)
 * @param reps          quantas revisões já foram feitas
 * @param lapses        quantas vezes foi esquecido (sempre 0 aqui: não há "Again")
 * @param lastReview    dia da última revisão (null se nunca revisado)
 * @param due           próximo dia de revisão
 * @param scheduledDays intervalo em dias até "due"
 */
public record FsrsCard(State state, double stability, double difficulty, int reps, int lapses, LocalDate lastReview,
        LocalDate due, int scheduledDays) {

    public enum State {
        NEW,
        LEARNING,
        REVIEW,
        RELEARNING
    }

    public static FsrsCard newCard() {
        return new FsrsCard(State.NEW, 0, 0, 0, 0, null, null, 0);
    }
}
