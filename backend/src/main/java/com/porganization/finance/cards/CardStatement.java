package com.porganization.finance.cards;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Fatura de um cartão, identificada pelo mês de vencimento. */
@Entity
@Table(name = "card_statements")
public class CardStatement {

    /** Estado guardado. "Fechada" não é guardado: é calculado na leitura (F07). */
    public enum StoredStatus {
        OPEN,
        PAID
    }

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "card_id", nullable = false, updatable = false)
    private UUID cardId;

    @Column(name = "reference_month", nullable = false, updatable = false)
    private LocalDate referenceMonth;

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StoredStatus status = StoredStatus.OPEN;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected CardStatement() {
    }

    public CardStatement(UUID userId, UUID cardId, StatementResolver.StatementPeriod period) {
        this.userId = userId;
        this.cardId = cardId;
        this.referenceMonth = period.referenceMonth().atDay(1);
        this.closingDate = period.closingDate();
        this.dueDate = period.dueDate();
    }

    public void markPaid(Instant when) {
        this.status = StoredStatus.PAID;
        this.paidAt = when;
    }

    /** Pagamento desfeito (o gasto do pagamento foi excluído): volta a contar no limite. */
    public void reopen() {
        this.status = StoredStatus.OPEN;
        this.paidAt = null;
    }

    /** OPEN, CLOSED (hoje já passou do fechamento) ou PAID. "Fechada" não é guardado, sai da data. */
    public String statusOn(LocalDate today) {
        if (status == StoredStatus.PAID) {
            return "PAID";
        }
        return today.isAfter(closingDate) ? "CLOSED" : "OPEN";
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public YearMonth getReferenceMonth() {
        return YearMonth.from(referenceMonth);
    }

    public LocalDate getClosingDate() {
        return closingDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public StoredStatus getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
