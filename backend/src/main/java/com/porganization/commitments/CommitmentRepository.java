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
}
