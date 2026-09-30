package com.porganization.studies;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Sempre filtrado pelo dono, como os demais repositórios. */
public interface TagRepository extends Repository<Tag, UUID> {

    Tag save(Tag tag);

    Tag saveAndFlush(Tag tag);

    void delete(Tag tag);

    Optional<Tag> findByIdAndUserId(UUID id, UUID userId);

    List<Tag> findByUserIdOrderByNameAsc(UUID userId);

    List<Tag> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    Optional<Tag> findByUserIdAndNameIgnoreCase(UUID userId, String name);
}
