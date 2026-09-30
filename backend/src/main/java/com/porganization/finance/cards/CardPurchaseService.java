package com.porganization.finance.cards;

import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.cards.CardDtos.PurchaseRequest;
import com.porganization.finance.cards.CardDtos.PurchaseResponse;
import com.porganization.finance.categories.Category;
import com.porganization.finance.transactions.Transaction;
import com.porganization.finance.transactions.TransactionRepository;
import com.porganization.finance.transactions.TransactionType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compras no cartão, à vista ou parceladas (1 a 48x). Cada parcela é uma transação na fatura
 * seguinte à da anterior, começando pela fatura da data da compra; todas com o mesmo purchase_id.
 */
@Service
public class CardPurchaseService {

    private final CreditCardService cards;
    private final TransactionRepository transactions;

    public CardPurchaseService(CreditCardService cards, TransactionRepository transactions) {
        this.cards = cards;
        this.transactions = transactions;
    }

    @Transactional
    public PurchaseResponse purchase(UUID userId, UUID cardId, PurchaseRequest request) {
        CreditCard card = cards.find(userId, cardId);
        Category category = cards.expenseCategory(userId, request.categoryId());
        int count = request.installments() == null ? 1 : request.installments();
        List<BigDecimal> values;
        try {
            values = InstallmentCalculator.split(request.amount(), count);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("installments", "valor pequeno demais para " + count + " parcelas");
        }

        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        CardStatement first = cards.statementFor(card, request.date());
        YearMonth firstClosingMonth = YearMonth.from(first.getClosingDate());
        UUID purchaseId = UUID.randomUUID();

        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CardStatement statement = i == 0 ? first
                    : cards.statementForPeriod(card, StatementResolver.forClosingMonth(card.getClosingDay(), card.getDueDay(),
                            firstClosingMonth.plusMonths(i)));
            // Todas as parcelas têm a data da compra: orçamentos contam no mês da compra (F09)
            Transaction t = new Transaction(userId, TransactionType.EXPENSE, values.get(i), request.date());
            t.setCategoryId(category.getId());
            t.setCardStatementId(statement.getId());
            t.setPurchaseId(purchaseId);
            t.setInstallment(i + 1, count);
            t.setDescription(count == 1 ? description : "%s (%d/%d)".formatted(description == null ? "Compra" : description, i + 1, count));
            t.setPaid(false);
            created.add(transactions.save(t).getId());
        }
        return new PurchaseResponse(purchaseId, purchaseId, created);
    }

    /** Exclui a compra inteira: todas as parcelas saem das faturas. */
    @Transactional
    public void delete(UUID userId, UUID purchaseId) {
        List<Transaction> installments = transactions.findByUserIdAndPurchaseId(userId, purchaseId);
        if (installments.isEmpty()) {
            throw new NotFoundException("Compra não encontrada");
        }
        installments.forEach(transactions::delete);
    }
}
