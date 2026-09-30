package com.porganization.finance.goals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface GoalContributionRepository extends Repository<GoalContribution, UUID> {

    GoalContribution save(GoalContribution contribution);

    void delete(GoalContribution contribution);

    Optional<GoalContribution> findByIdAndUserIdAndGoalId(UUID id, UUID userId, UUID goalId);

    List<GoalContribution> findByUserIdAndGoalIdOrderByDateDescCreatedAtDesc(UUID userId, UUID goalId);

    @Query("""
            select new com.porganization.finance.goals.GoalTotal(c.goalId, sum(c.amount))
            from GoalContribution c where c.userId = :userId group by c.goalId
            """)
    List<GoalTotal> totalsByGoal(UUID userId);
}
