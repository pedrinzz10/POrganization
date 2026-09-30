package com.porganization.finance.goals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface SavingsGoalRepository extends Repository<SavingsGoal, UUID> {

    SavingsGoal save(SavingsGoal goal);

    void delete(SavingsGoal goal);

    Optional<SavingsGoal> findByIdAndUserId(UUID id, UUID userId);

    List<SavingsGoal> findByUserIdOrderByArchivedAscNameAsc(UUID userId);
}
