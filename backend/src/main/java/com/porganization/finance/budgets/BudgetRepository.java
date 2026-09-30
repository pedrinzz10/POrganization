package com.porganization.finance.budgets;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface BudgetRepository extends Repository<Budget, UUID> {

    Budget save(Budget budget);

    void delete(Budget budget);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    /** Os orçamentos que podem valer no mês: os recorrentes e os daquele mês. */
    @Query("select b from Budget b where b.userId = :userId and (b.month is null or b.month = :month)")
    List<Budget> candidatesFor(UUID userId, LocalDate month);

    boolean existsByUserIdAndCategoryIdAndMonthIsNull(UUID userId, UUID categoryId);

    boolean existsByUserIdAndCategoryIdAndMonth(UUID userId, UUID categoryId, LocalDate month);
}
