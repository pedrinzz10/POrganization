package com.porganization.finance.categories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface CategoryRepository extends Repository<Category, UUID> {

    Category save(Category category);

    void delete(Category category);

    void flush();

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    List<Category> findByUserIdOrderByKindAscNameAsc(UUID userId);

    Optional<Category> findByUserIdAndKindAndNameIgnoreCase(UUID userId, CategoryKind kind, String name);

    /** 1 se marcou agora (primeiro acesso), 0 se o usuário já tinha recebido as categorias padrão. */
    @Modifying
    @Query(value = "insert into finance_setup (user_id) values (:userId) on conflict (user_id) do nothing", nativeQuery = true)
    int markSeeded(UUID userId);
}
