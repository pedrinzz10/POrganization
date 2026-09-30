package com.porganization.finance.transactions;

import com.porganization.finance.categories.FinanceTag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_id")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "description")
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "card_statement_id")
    private UUID cardStatementId;

    @Column(name = "purchase_id")
    private UUID purchaseId;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "installment_count")
    private Integer installmentCount;

    @Column(name = "transfer_group_id")
    private UUID transferGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_direction")
    private TransferDirection transferDirection;

    @ManyToMany
    @JoinTable(name = "transaction_tags", joinColumns = @JoinColumn(name = "transaction_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<FinanceTag> tags = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Transaction() {
    }

    public Transaction(UUID userId, TransactionType type, BigDecimal amount, LocalDate date) {
        this.userId = userId;
        this.type = type;
        setAmount(amount);
        this.date = date;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public void setAccountId(UUID accountId) {
        this.accountId = accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public UUID getCardStatementId() {
        return cardStatementId;
    }

    public void setCardStatementId(UUID cardStatementId) {
        this.cardStatementId = cardStatementId;
    }

    public UUID getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(UUID purchaseId) {
        this.purchaseId = purchaseId;
    }

    public Integer getInstallmentNumber() {
        return installmentNumber;
    }

    public Integer getInstallmentCount() {
        return installmentCount;
    }

    public void setInstallment(Integer number, Integer count) {
        this.installmentNumber = number;
        this.installmentCount = count;
    }

    public UUID getTransferGroupId() {
        return transferGroupId;
    }

    public TransferDirection getTransferDirection() {
        return transferDirection;
    }

    /** Transforma esta transação numa perna de transferência. */
    public void setTransferLeg(UUID groupId, TransferDirection direction) {
        this.type = TransactionType.TRANSFER;
        this.transferGroupId = groupId;
        this.transferDirection = direction;
        this.categoryId = null;
    }

    public Set<FinanceTag> getTags() {
        return tags;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
