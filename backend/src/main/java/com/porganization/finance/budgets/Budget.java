package com.porganization.finance.budgets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Orçamento de uma categoria de gasto: de um mês, ou recorrente (month null) para todo mês. */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "month", updatable = false)
    private LocalDate month;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Budget() {
    }

    public Budget(UUID userId, UUID categoryId, YearMonth month, BigDecimal amount) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.month = month == null ? null : month.atDay(1);
        setAmount(amount);
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    /** null = recorrente (vale todo mês que não tem um orçamento próprio). */
    public YearMonth getMonth() {
        return month == null ? null : YearMonth.from(month);
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
