package com.porganization.finance.categories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface FinanceTagRepository extends Repository<FinanceTag, UUID> {

    FinanceTag save(FinanceTag tag);

    void delete(FinanceTag tag);

    Optional<FinanceTag> findByIdAndUserId(UUID id, UUID userId);

    List<FinanceTag> findByUserIdOrderByNameAsc(UUID userId);

    List<FinanceTag> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);

    Optional<FinanceTag> findByUserIdAndNameIgnoreCase(UUID userId, String name);
}
