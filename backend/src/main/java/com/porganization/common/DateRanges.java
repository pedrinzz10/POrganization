package com.porganization.common;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Validação dos intervalos de datas (from/to) aceitos pela API. */
public final class DateRanges {

    /** Maior intervalo consultável de uma vez: cabe a visão de ano com folga. */
    public static final int MAX_DAYS = 400;

    private DateRanges() {
    }

    /** "to" igual ou depois de "from" e no máximo MAX_DAYS dias entre eles; senão 400 no campo "to". */
    public static void validate(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidRequestException("to", "deve ser igual ou depois de from");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new InvalidRequestException("to", "o intervalo pode ter no máximo " + MAX_DAYS + " dias");
        }
    }
}
