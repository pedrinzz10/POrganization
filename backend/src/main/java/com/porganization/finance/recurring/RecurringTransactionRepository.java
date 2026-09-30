package com.porganization.finance.recurring;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface RecurringTransactionRepository extends Repository<RecurringTransaction, UUID> {

    RecurringTransaction save(RecurringTransaction recurring);

    void delete(RecurringTransaction recurring);

    Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId);

    List<RecurringTransaction> findByUserIdOrderByDayOfMonthAscDescriptionAsc(UUID userId);

    /** Modelos que valem no mês (primeiro dia do mês). */
    @Query("""
            select r from RecurringTransaction r
            where r.userId = :userId and r.startMonth <= :month and (r.endMonth is null or r.endMonth >= :month)
            """)
    List<RecurringTransaction> activeIn(UUID userId, LocalDate month);

    /** Donos de algum modelo ativo no mês: quem o job diário precisa gerar. */
    @Query("""
            select distinct r.userId from RecurringTransaction r
            where r.startMonth <= :month and (r.endMonth is null or r.endMonth >= :month)
            """)
    List<UUID> usersActiveIn(LocalDate month);

    /**
     * Marca o mês como gerado. Devolve 1 se marcou agora e 0 se já estava marcado; atômico, então
     * duas consultas simultâneas do mesmo mês geram uma transação só.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into recurring_generations (recurring_id, month) values (:recurringId, :month)
            on conflict do nothing
            """, nativeQuery = true)
    int markGenerated(UUID recurringId, LocalDate month);
}
