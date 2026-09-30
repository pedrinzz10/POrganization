package com.porganization.finance.recurring;

import com.porganization.finance.transactions.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Modelo de gasto ou renda fixo: gera uma transação por mês entre startMonth e endMonth. */
@Entity
@Table(name = "recurring_transactions")
public class RecurringTransaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "description")
    private String description;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "start_month", nullable = false)
    private LocalDate startMonth;

    @Column(name = "end_month")
    private LocalDate endMonth;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RecurringTransaction() {
    }

    public RecurringTransaction(UUID userId) {
        this.userId = userId;
    }

    public void update(TransactionType type, BigDecimal amount, String description, UUID accountId, UUID cardId,
            UUID categoryId, int dayOfMonth, YearMonth startMonth, YearMonth endMonth) {
        this.type = type;
        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        this.description = description;
        this.accountId = accountId;
        this.cardId = cardId;
        this.categoryId = categoryId;
        this.dayOfMonth = dayOfMonth;
        this.startMonth = startMonth.atDay(1);
        this.endMonth = endMonth == null ? null : endMonth.atDay(1);
    }

    /** O modelo vale para esse mês (entre o início e o fim, inclusivos)? */
    public boolean activeIn(YearMonth month) {
        return !month.isBefore(getStartMonth()) && (endMonth == null || !month.isAfter(getEndMonth()));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public int getDayOfMonth() {
        return dayOfMonth;
    }

    public YearMonth getStartMonth() {
        return YearMonth.from(startMonth);
    }

    public YearMonth getEndMonth() {
        return endMonth == null ? null : YearMonth.from(endMonth);
    }
}
