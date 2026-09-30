package com.porganization.finance.cards;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CardStatementRepository extends Repository<CardStatement, UUID> {

    CardStatement save(CardStatement statement);

    /** Cria a fatura se ainda não existir; atômico, então duas compras simultâneas não duplicam. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into card_statements (user_id, card_id, reference_month, closing_date, due_date)
            values (:userId, :cardId, :referenceMonth, :closingDate, :dueDate)
            on conflict (card_id, reference_month) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(UUID userId, UUID cardId, LocalDate referenceMonth, LocalDate closingDate, LocalDate dueDate);

    Optional<CardStatement> findByIdAndUserId(UUID id, UUID userId);

    Optional<CardStatement> findByUserIdAndCardIdAndReferenceMonth(UUID userId, UUID cardId, LocalDate referenceMonth);

    List<CardStatement> findByUserIdAndCardIdOrderByReferenceMonthAsc(UUID userId, UUID cardId);

    List<CardStatement> findByUserIdAndDueDateBetweenOrderByDueDateAsc(UUID userId, LocalDate from, LocalDate to);
}
