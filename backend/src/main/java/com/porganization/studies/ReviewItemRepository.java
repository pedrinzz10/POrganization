package com.porganization.studies;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ReviewItemRepository extends Repository<ReviewItem, UUID> {

    ReviewItem save(ReviewItem item);

    Optional<ReviewItem> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    /** Revisões vencidas até "date", da mais atrasada para a mais recente. */
    List<ReviewItem> findByUserIdAndDueDateLessThanEqualOrderByDueDateAsc(UUID userId, LocalDate date);
}
