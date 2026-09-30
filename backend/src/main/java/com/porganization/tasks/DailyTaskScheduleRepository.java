package com.porganization.tasks;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface DailyTaskScheduleRepository extends Repository<DailyTaskSchedule, UUID> {

    DailyTaskSchedule save(DailyTaskSchedule schedule);

    List<DailyTaskSchedule> findByTaskIdIn(Collection<UUID> taskIds);

    Optional<DailyTaskSchedule> findByTaskIdAndValidFrom(UUID taskId, LocalDate validFrom);
}
