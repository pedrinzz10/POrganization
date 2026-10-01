package com.porganization.finance.cards;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class CardDtos {

    private CardDtos() {
    }

    public record CardRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal creditLimit,
            @NotNull @Min(1) @Max(31) Integer closingDay,
            @NotNull @Min(1) @Max(31) Integer dueDay,
            @NotNull UUID paymentAccountId) {
    }

    public record CardResponse(UUID id, String name, BigDecimal creditLimit, int closingDay, int dueDay,
            UUID paymentAccountId, boolean archived) {
    }

    /** Compra no cartão. installments (1 a 48) é usado a partir da F06. */
    public record PurchaseRequest(
            @NotNull @DecimalMin(value = "0.01", message = "deve ser maior que zero") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull LocalDate date,
            @Size(max = 200) String description,
            @NotNull UUID categoryId,
            @Min(1) @Max(48) Integer installments) {
    }

    /** id e purchaseId são o mesmo valor: o identificador da compra (todas as parcelas). */
    public record PurchaseResponse(UUID id, UUID purchaseId, List<UUID> transactionIds) {
    }

    /** status: OPEN, CLOSED (hoje depois do fechamento, calculado na leitura) ou PAID. */
    public record StatementSummary(UUID id, UUID cardId, YearMonth referenceMonth, LocalDate closingDate, LocalDate dueDate,
            String status, BigDecimal total) {
    }

    /** Uma compra (ou parcela) dentro da fatura. */
    /** recurringId: lançado por um agendado no cartão (assinatura). */
    public record StatementItem(UUID id, LocalDate date, String description, BigDecimal amount, UUID categoryId,
            UUID purchaseId, Integer installmentNumber, Integer installmentCount, UUID recurringId) {
    }

    /**
     * Fatura de um mês com os itens. id é null quando ainda não há compra nessa fatura.
     * availableLimit = limite − compras de todas as faturas não pagas do cartão.
     */
    public record StatementResponse(UUID id, UUID cardId, YearMonth referenceMonth, LocalDate closingDate, LocalDate dueDate,
            String status, BigDecimal total, BigDecimal creditLimit, BigDecimal availableLimit, Instant paidAt,
            UUID paymentTransactionId, List<StatementItem> items) {
    }
}
