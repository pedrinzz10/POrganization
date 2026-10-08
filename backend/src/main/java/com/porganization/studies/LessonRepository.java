package com.porganization.studies;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface LessonRepository extends Repository<Lesson, UUID> {

    Lesson save(Lesson lesson);

    Lesson saveAndFlush(Lesson lesson);

    Optional<Lesson> findByIdAndUserId(UUID id, UUID userId);

    List<Lesson> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    List<Lesson> findByUserIdAndStudiedAtGreaterThanEqualAndStudiedAtLessThanOrderByStudiedAtDesc(UUID userId,
            Instant from, Instant toExclusive);
}
