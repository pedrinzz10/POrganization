package com.porganization.commitments;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface OccurrenceOverrideRepository extends Repository<OccurrenceOverride, UUID> {

    OccurrenceOverride save(OccurrenceOverride override);

    Optional<OccurrenceOverride> findByUserIdAndCommitmentIdAndOccurrenceDate(UUID userId, UUID commitmentId,
            LocalDate occurrenceDate);

    List<OccurrenceOverride> findByUserIdAndCommitmentIdInAndOccurrenceDateBetween(UUID userId,
            Collection<UUID> commitmentIds, LocalDate from, LocalDate to);

    /** Dias cancelados de uma série (viram EXDATE no Google). */
    List<OccurrenceOverride> findByUserIdAndCommitmentIdAndCancelledTrueOrderByOccurrenceDateAsc(UUID userId, UUID commitmentId);
}
