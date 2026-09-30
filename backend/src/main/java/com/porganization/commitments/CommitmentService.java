package com.porganization.commitments;

import com.porganization.commitments.dto.CommitmentRequest;
import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitmentService {

    /** Maior intervalo consultável de uma vez: cabe a visão de ano com folga. */
    public static final int MAX_RANGE_DAYS = 400;

    /** Por data; no mesmo dia, os de dia todo primeiro, depois por horário e título. */
    static final Comparator<Commitment> CHRONOLOGICAL = Comparator.comparing(Commitment::getDate)
            .thenComparing(c -> c.isAllDay() ? LocalTime.MIN : c.getStartTime(),
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(c -> !c.isAllDay())
            .thenComparing(Commitment::getTitle);

    private final CommitmentRepository repository;

    public CommitmentService(CommitmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Commitment create(UUID userId, CommitmentRequest request) {
        Commitment commitment = new Commitment(userId, request.title().trim(), request.date());
        apply(commitment, request);
        return repository.save(commitment);
    }

    @Transactional(readOnly = true)
    public Commitment get(UUID userId, UUID id) {
        // Compromisso de outro usuário cai aqui também: 404, sem revelar que o id existe
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Compromisso não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<Commitment> findInRange(UUID userId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return repository.findByUserIdAndDateBetween(userId, from, to).stream().sorted(CHRONOLOGICAL).toList();
    }

    static void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidRequestException("to", "deve ser igual ou depois de from");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new InvalidRequestException("to", "o intervalo pode ter no máximo " + MAX_RANGE_DAYS + " dias");
        }
    }

    @Transactional
    public Commitment update(UUID userId, UUID id, CommitmentRequest request) {
        Commitment commitment = get(userId, id);
        commitment.setTitle(request.title().trim());
        commitment.setDate(request.date());
        apply(commitment, request);
        return commitment;
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(get(userId, id));
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
        commitment.setRecurrenceRule(request.recurrenceRule());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
