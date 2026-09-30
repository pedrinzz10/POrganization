package com.porganization.tasks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface DailyTaskRepository extends Repository<DailyTask, UUID> {

    DailyTask save(DailyTask task);

    void delete(DailyTask task);

    Optional<DailyTask> findByIdAndUserId(UUID id, UUID userId);

    List<DailyTask> findByUserIdOrderByPositionAscTitleAsc(UUID userId);

    List<DailyTask> findByUserIdAndArchivedFalseOrderByPositionAscTitleAsc(UUID userId);
}
