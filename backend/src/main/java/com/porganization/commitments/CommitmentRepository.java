package com.porganization.commitments;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Toda consulta recebe o userId: não existe método que devolva compromisso sem filtrar pelo dono.
 * Por isso estende Repository (e não JpaRepository, que traria findById/findAll sem filtro).
 */
public interface CommitmentRepository extends Repository<Commitment, UUID> {

    Commitment save(Commitment commitment);

    Commitment saveAndFlush(Commitment commitment);

    void delete(Commitment commitment);

    Optional<Commitment> findByIdAndUserId(UUID id, UUID userId);

    List<Commitment> findByUserIdAndDateBetween(UUID userId, LocalDate from, LocalDate to);

    /** Compromissos únicos (sem recorrência) com a data no intervalo. */
    List<Commitment> findByUserIdAndRecurrenceRuleIsNullAndDateBetween(UUID userId, LocalDate from, LocalDate to);

    /** Séries que começaram até "to"; a expansão decide quais dias caem no intervalo. */
    List<Commitment> findByUserIdAndRecurrenceRuleIsNotNullAndDateLessThanEqual(UUID userId, LocalDate to);

    /** Mudanças que não chegaram ao Google (de todos os usuários): usado só pelo cron da sincronização. */
    List<Commitment> findBySyncPendingTrue();
}
