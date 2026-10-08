package com.porganization.studies;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Sempre filtrado pelo dono, como os demais repositórios. */
public interface SubjectRepository extends Repository<Subject, UUID> {

    Subject save(Subject subject);

    Subject saveAndFlush(Subject subject);

    /** Grava as mudanças pendentes antes das consultas por SQL (pré-requisitos e contagens). */
    void flush();

    void delete(Subject subject);

    Optional<Subject> findByIdAndUserId(UUID id, UUID userId);

    List<Subject> findByUserIdOrderByPriorityOrderAsc(UUID userId);

    @Query("select coalesce(max(s.priorityOrder), 0) from Subject s where s.userId = :userId")
    int maxPriorityOrder(UUID userId);
}
