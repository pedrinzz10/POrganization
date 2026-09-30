package com.porganization.finance.recurring;

/** Como a data de cada mês de um agendado é calculada. */
public enum ScheduleRule {
    /** Dia N do mês (31 em mês curto = último dia), com ajuste se cair em dia não útil. */
    DAY_OF_MONTH,
    /** N-ésimo dia útil do mês (ex.: salário no 5º dia útil). */
    BUSINESS_DAY,
    /** Último dia útil do mês. */
    LAST_BUSINESS_DAY
}
