package com.porganization.finance.transactions;

import com.porganization.finance.categories.FinanceTag;
import jakarta.persistence.criteria.Join;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Filtros combináveis da listagem de transações. Toda consulta parte de ownedBy (o dono). */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> ownedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Transaction> inMonth(YearMonth month) {
        return (root, query, cb) -> cb.between(root.get("date"), month.atDay(1), month.atEndOfMonth());
    }

    public static Specification<Transaction> inAccount(UUID accountId) {
        return (root, query, cb) -> cb.equal(root.get("accountId"), accountId);
    }

    public static Specification<Transaction> inCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<Transaction> ofType(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Transaction> taggedWith(UUID tagId) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Transaction, FinanceTag> tags = root.join("tags");
            return cb.equal(tags.get("id"), tagId);
        };
    }

    /** Monta a consulta só com os filtros informados (os nulos não filtram). */
    public static Specification<Transaction> of(UUID userId, YearMonth month, UUID accountId, UUID categoryId,
            UUID tagId, TransactionType type) {
        Specification<Transaction> spec = ownedBy(userId);
        if (month != null) {
            spec = spec.and(inMonth(month));
        }
        if (accountId != null) {
            spec = spec.and(inAccount(accountId));
        }
        if (categoryId != null) {
            spec = spec.and(inCategory(categoryId));
        }
        if (tagId != null) {
            spec = spec.and(taggedWith(tagId));
        }
        if (type != null) {
            spec = spec.and(ofType(type));
        }
        return spec;
    }
}
