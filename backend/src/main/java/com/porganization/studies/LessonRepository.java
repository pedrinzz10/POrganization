package com.porganization.studies;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface LessonRepository extends Repository<Lesson, UUID> {

    Lesson save(Lesson lesson);

    Optional<Lesson> findByIdAndUserId(UUID id, UUID userId);
}
