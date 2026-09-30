package com.porganization.finance.recurring;

import java.time.LocalDate;

/**
 * Situação de uma ocorrência de agendado em conta. Só CONFIRMED entra no saldo; as outras (menos
 * CANCELLED) contam como previstas.
 */
public enum OccurrenceStatus {
    /** Data futura. */
    EXPECTED,
    /** A data é hoje: falta confirmar. */
    TO_CONFIRM,
    /** A data passou e ninguém confirmou, remarcou ou cancelou. */
    OVERDUE,
    /** Recebido ou pago. */
    CONFIRMED,
    /** Remarcada para uma data futura (a original fica guardada). */
    RESCHEDULED,
    /** "Não vou receber/pagar este mês". */
    CANCELLED;

    /**
     * Estado de uma ocorrência que ainda existe como lançamento. Remarcada vale como RESCHEDULED só
     * enquanto a nova data não chega; no dia, volta a pedir confirmação como qualquer outra.
     */
    public static OccurrenceStatus of(boolean paid, LocalDate date, LocalDate scheduledDate, LocalDate today) {
        if (paid) {
            return CONFIRMED;
        }
        if (date.isAfter(today)) {
            return scheduledDate != null && !scheduledDate.equals(date) ? RESCHEDULED : EXPECTED;
        }
        return date.isEqual(today) ? TO_CONFIRM : OVERDUE;
    }
}
