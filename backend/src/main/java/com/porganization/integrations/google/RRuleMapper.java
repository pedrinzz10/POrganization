package com.porganization.integrations.google;

import com.porganization.commitments.recurrence.Frequency;
import com.porganization.commitments.recurrence.RecurrenceRule;
import com.porganization.commitments.recurrence.WeekDay;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RecurrenceRule do app → linhas RFC 5545 do campo recurrence do Google Calendar.
 * Ex.: semanal seg/qua até 31/12 → "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261231T235959Z".
 */
public final class RRuleMapper {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HHmmss");

    private RRuleMapper() {
    }

    /** Evento com horário: UNTIL em UTC até o fim do dia. */
    public static String toRRule(RecurrenceRule rule) {
        return toRRule(rule, false);
    }

    /** Evento de dia todo: UNTIL como data, do mesmo tipo do início (exigência da RFC 5545). */
    public static String toRRule(RecurrenceRule rule, boolean allDay) {
        List<String> parts = new ArrayList<>();
        parts.add("FREQ=" + rule.freq().name());
        if (rule.interval() != null && rule.interval() > 1) {
            parts.add("INTERVAL=" + rule.interval());
        }
        if (rule.byWeekDays() != null && !rule.byWeekDays().isEmpty()) {
            parts.add("BYDAY=" + rule.byWeekDays().stream().sorted().map(RRuleMapper::day).collect(Collectors.joining(",")));
        }
        if (rule.count() != null) {
            parts.add("COUNT=" + rule.count());
        } else if (rule.until() != null) {
            parts.add("UNTIL=" + rule.until().format(DATE) + (allDay ? "" : "T235959Z"));
        }
        return "RRULE:" + String.join(";", parts);
    }

    /** Dias cancelados da série: "EXDATE;TZID=America/Sao_Paulo:20261005T070000" ou, dia todo, "EXDATE;VALUE=DATE:20261005". */
    public static String toExDate(List<LocalDate> dates, LocalTime startTime, String timeZone) {
        if (startTime == null) {
            return "EXDATE;VALUE=DATE:" + dates.stream().map(d -> d.format(DATE)).collect(Collectors.joining(","));
        }
        return "EXDATE;TZID=" + timeZone + ":"
                + dates.stream().map(d -> d.format(DATE) + "T" + startTime.format(TIME)).collect(Collectors.joining(","));
    }

    /**
     * Caminho inverso (importação, I08): o recurrence de um evento do Google → RecurrenceRule, quando a
     * regra cabe no modelo do app (FREQ, INTERVAL, BYDAY simples na semanal, COUNT, UNTIL). Regras
     * mais ricas (BYMONTHDAY, BYSETPOS, "2MO"...) → vazio, e o evento entra como único.
     */
    public static Optional<RecurrenceRule> fromRRule(List<String> recurrence) {
        String line = recurrence == null ? null
                : recurrence.stream().filter(r -> r.startsWith("RRULE:")).findFirst().orElse(null);
        if (line == null) {
            return Optional.empty();
        }
        try {
            Frequency freq = null;
            Integer interval = null;
            List<WeekDay> days = null;
            Integer count = null;
            LocalDate until = null;
            for (String part : line.substring("RRULE:".length()).split(";")) {
                String[] kv = part.split("=", 2);
                switch (kv[0]) {
                    case "FREQ" -> freq = Frequency.valueOf(kv[1]);
                    case "INTERVAL" -> interval = Integer.parseInt(kv[1]);
                    case "BYDAY" -> days = Arrays.stream(kv[1].split(",")).map(RRuleMapper::weekDay).toList();
                    case "COUNT" -> count = Integer.parseInt(kv[1]);
                    case "UNTIL" -> until = LocalDate.parse(kv[1].substring(0, 8), DATE);
                    case "WKST" -> {
                        // início da semana: não muda as datas que o app gera
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
            }
            if (freq == null || (days != null && freq != Frequency.WEEKLY)) {
                return Optional.empty();
            }
            return Optional.of(new RecurrenceRule(freq, interval, days, until, count));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static WeekDay weekDay(String code) {
        return Arrays.stream(WeekDay.values()).filter(d -> day(d).equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(code));
    }

    private static String day(WeekDay day) {
        return day.name().substring(0, 2);
    }
}
