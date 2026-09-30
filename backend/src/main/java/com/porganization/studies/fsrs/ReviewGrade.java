package com.porganization.studies.fsrs;

/**
 * Nota que o usuário dá ao terminar uma revisão, mapeada para o rating do FSRS. Não existe
 * "Again" (1): a revisão é uma mini aula, então sempre houve alguma lembrança.
 */
public enum ReviewGrade {
    DIFICIL(2),
    OK(3),
    FACIL(4);

    private final int rating;

    ReviewGrade(int rating) {
        this.rating = rating;
    }

    /** Rating do FSRS: Hard = 2, Good = 3, Easy = 4. */
    public int rating() {
        return rating;
    }
}
