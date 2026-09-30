package com.porganization.notifications;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ReminderRepository extends Repository<Reminder, UUID> {

    Reminder save(Reminder reminder);

    void delete(Reminder reminder);

    List<Reminder> findAll();

    List<Reminder> findByUserIdAndCommitmentIdOrderByMinutesBeforeAsc(UUID userId, UUID commitmentId);

    List<Reminder> findByUserIdAndCommitmentIdIn(UUID userId, List<UUID> commitmentIds);
}
