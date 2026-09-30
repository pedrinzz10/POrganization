package com.porganization.commitments;

import com.porganization.commitments.dto.CommitmentRequest;
import com.porganization.commitments.dto.OccurrencePatch;
import com.porganization.commitments.dto.OccurrenceResponse;
import com.porganization.commitments.recurrence.RecurrenceExpander;
import com.porganization.common.DateRanges;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.notifications.ReminderService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitmentService {

    /** Por data; no mesmo dia, os de dia todo primeiro, depois por horário e título. */
    static final Comparator<OccurrenceResponse> CHRONOLOGICAL = Comparator.comparing(OccurrenceResponse::occurrenceDate)
            .thenComparing(o -> !o.allDay())
            .thenComparing(OccurrenceResponse::startTime, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(OccurrenceResponse::title);

    private final CommitmentRepository repository;
    private final OccurrenceOverrideRepository overrides;
    private final ReminderService reminders;

    public CommitmentService(CommitmentRepository repository, OccurrenceOverrideRepository overrides,
            ReminderService reminders) {
        this.repository = repository;
        this.overrides = overrides;
        this.reminders = reminders;
    }

    @Transactional
    public Commitment create(UUID userId, CommitmentRequest request) {
        Commitment commitment = new Commitment(userId, request.title().trim(), request.date());
        apply(commitment, request);
        Commitment saved = repository.save(commitment);
        reminders.onCreate(userId, saved.getId(), request.reminders());
        return saved;
    }

    @Transactional(readOnly = true)
    public Commitment get(UUID userId, UUID id) {
        // Compromisso de outro usuário cai aqui também: 404, sem revelar que o id existe
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Compromisso não encontrado"));
    }

    /**
     * Ocorrências de from a to: os compromissos únicos do intervalo mais as séries expandidas
     * pelo RecurrenceExpander, com os ajustes de cada dia aplicados, em ordem cronológica.
     */
    @Transactional(readOnly = true)
    public List<OccurrenceResponse> findInRange(UUID userId, LocalDate from, LocalDate to) {
        DateRanges.validate(from, to);
        List<OccurrenceResponse> occurrences = new ArrayList<>();
        for (Commitment single : repository.findByUserIdAndRecurrenceRuleIsNullAndDateBetween(userId, from, to)) {
            occurrences.add(occurrence(single, single.getDate(), null));
        }

        List<Commitment> series = repository.findByUserIdAndRecurrenceRuleIsNotNullAndDateLessThanEqual(userId, to);
        Map<OverrideKey, OccurrenceOverride> adjustments = series.isEmpty() ? Map.of()
                : overrides.findByUserIdAndCommitmentIdInAndOccurrenceDateBetween(userId,
                                series.stream().map(Commitment::getId).toList(), from, to).stream()
                        .collect(Collectors.toMap(o -> new OverrideKey(o.getCommitmentId(), o.getOccurrenceDate()),
                                Function.identity()));

        for (Commitment s : series) {
            for (LocalDate date : RecurrenceExpander.expand(s.getDate(), s.getRecurrenceRule(), from, to)) {
                OccurrenceOverride adjustment = adjustments.get(new OverrideKey(s.getId(), date));
                if (adjustment == null || !adjustment.isCancelled()) {
                    occurrences.add(occurrence(s, date, adjustment));
                }
            }
        }
        occurrences.sort(CHRONOLOGICAL);
        return occurrences;
    }

    /**
     * Ajusta só a ocorrência de um dia de um compromisso recorrente (concluir, cancelar,
     * trocar título ou horário). A série não muda.
     */
    @Transactional
    public OccurrenceResponse patchOccurrence(UUID userId, UUID id, LocalDate date, OccurrencePatch patch) {
        Commitment series = get(userId, id);
        if (!series.isRecurring()) {
            throw new InvalidRequestException("id", "compromisso não é recorrente; use PATCH /api/commitments/{id}/done");
        }
        if (RecurrenceExpander.expand(series.getDate(), series.getRecurrenceRule(), date, date).isEmpty()) {
            throw new NotFoundException("Ocorrência não encontrada");
        }
        if (patch.title() != null && patch.title().isBlank()) {
            throw new InvalidRequestException("title", "não deve estar em branco");
        }

        OccurrenceOverride adjustment = overrides.findByUserIdAndCommitmentIdAndOccurrenceDate(userId, id, date)
                .orElseGet(() -> new OccurrenceOverride(userId, id, date));
        if (patch.done() != null) {
            adjustment.setDone(patch.done());
        }
        if (patch.cancelled() != null) {
            adjustment.setCancelled(patch.cancelled());
        }
        if (patch.title() != null) {
            adjustment.setOverrideTitle(patch.title().trim());
        }
        if (patch.startTime() != null) {
            adjustment.setOverrideTime(patch.startTime());
        }
        return occurrence(series, date, overrides.save(adjustment));
    }

    /** Concluir/desfazer um compromisso único. Para recorrentes, cada dia é concluído à parte. */
    @Transactional
    public Commitment setDone(UUID userId, UUID id, boolean done) {
        Commitment commitment = get(userId, id);
        if (commitment.isRecurring()) {
            throw new InvalidRequestException("id",
                    "compromisso recorrente: conclua a ocorrência com PATCH /api/commitments/{id}/occurrences/{data}");
        }
        commitment.setDone(done);
        return commitment;
    }

    @Transactional
    public Commitment update(UUID userId, UUID id, CommitmentRequest request) {
        Commitment commitment = get(userId, id);
        commitment.setTitle(request.title().trim());
        commitment.setDate(request.date());
        apply(commitment, request);
        reminders.onUpdate(userId, id, request.reminders());
        return commitment;
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(get(userId, id));
    }

    private static OccurrenceResponse occurrence(Commitment c, LocalDate date, OccurrenceOverride adjustment) {
        if (adjustment == null) {
            boolean done = !c.isRecurring() && c.isDone();
            return new OccurrenceResponse(c.getId(), date, c.getTitle(), c.getStartTime(), c.getEndTime(),
                    c.isAllDay(), done, c.isRecurring(), c.getDescription(), c.getLocation());
        }
        String title = adjustment.getOverrideTitle() != null ? adjustment.getOverrideTitle() : c.getTitle();
        LocalTime start = c.getStartTime();
        LocalTime end = c.getEndTime();
        boolean allDay = c.isAllDay();
        if (adjustment.getOverrideTime() != null) {
            // O fim acompanha o novo início, mantendo a duração (se não passar da meia-noite)
            if (start != null && end != null) {
                Duration duration = Duration.between(start, end);
                LocalTime shifted = adjustment.getOverrideTime().plus(duration);
                end = shifted.isAfter(adjustment.getOverrideTime()) ? shifted : null;
            } else {
                end = null;
            }
            start = adjustment.getOverrideTime();
            allDay = false;
        }
        return new OccurrenceResponse(c.getId(), date, title, start, end, allDay, adjustment.isDone(), true,
                c.getDescription(), c.getLocation());
    }

    private void apply(Commitment commitment, CommitmentRequest request) {
        boolean allDay = Boolean.TRUE.equals(request.allDay()) || request.startTime() == null;
        if (!allDay && request.endTime() != null && !request.endTime().isAfter(request.startTime())) {
            throw new InvalidRequestException("endTime", "deve ser depois do horário de início");
        }
        if (request.startTime() == null && request.endTime() != null && !Boolean.TRUE.equals(request.allDay())) {
            throw new InvalidRequestException("startTime", "informe o horário de início");
        }
        commitment.setAllDay(allDay);
        commitment.setStartTime(allDay ? null : request.startTime());
        commitment.setEndTime(allDay ? null : request.endTime());
        commitment.setDescription(blankToNull(request.description()));
        commitment.setLocation(blankToNull(request.location()));
        if (request.recurrenceRule() != null) {
            RecurrenceExpander.validate(request.date(), request.recurrenceRule());
        }
        commitment.setRecurrenceRule(request.recurrenceRule());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record OverrideKey(UUID commitmentId, LocalDate date) {
    }
}
