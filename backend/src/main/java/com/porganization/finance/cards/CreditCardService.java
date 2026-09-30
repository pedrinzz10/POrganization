package com.porganization.finance.cards;

import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.accounts.AccountRepository;
import com.porganization.finance.cards.CardDtos.CardRequest;
import com.porganization.finance.cards.CardDtos.CardResponse;
import com.porganization.finance.cards.CardDtos.PurchaseRequest;
import com.porganization.finance.cards.CardDtos.StatementSummary;
import com.porganization.finance.categories.Category;
import com.porganization.finance.categories.CategoryKind;
import com.porganization.finance.categories.CategoryRepository;
import com.porganization.finance.transactions.Transaction;
import com.porganization.finance.transactions.TransactionRepository;
import com.porganization.finance.transactions.TransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditCardService {

    private final CreditCardRepository cards;
    private final CardStatementRepository statements;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;

    public CreditCardService(CreditCardRepository cards, CardStatementRepository statements, AccountRepository accounts,
            CategoryRepository categories, TransactionRepository transactions) {
        this.cards = cards;
        this.statements = statements;
        this.accounts = accounts;
        this.categories = categories;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<CardResponse> list(UUID userId) {
        return cards.findByUserIdOrderByNameAsc(userId).stream().map(CreditCardService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CardResponse get(UUID userId, UUID id) {
        return toResponse(find(userId, id));
    }

    @Transactional
    public CardResponse create(UUID userId, CardRequest request) {
        requireAccount(userId, request.paymentAccountId());
        return toResponse(cards.save(new CreditCard(userId, request.name().trim(), request.creditLimit(), request.closingDay(),
                request.dueDay(), request.paymentAccountId())));
    }

    @Transactional
    public CardResponse update(UUID userId, UUID id, CardRequest request) {
        requireAccount(userId, request.paymentAccountId());
        CreditCard card = find(userId, id);
        card.update(request.name().trim(), request.creditLimit(), request.closingDay(), request.dueDay(),
                request.paymentAccountId());
        return toResponse(card);
    }

    /** Compra à vista no cartão: gasto sem conta, ligado à fatura certa. Não mexe em saldo de conta. */
    @Transactional
    public UUID purchase(UUID userId, UUID cardId, PurchaseRequest request) {
        CreditCard card = find(userId, cardId);
        Category category = expenseCategory(userId, request.categoryId());
        CardStatement statement = statementFor(card, request.date());
        Transaction t = new Transaction(userId, TransactionType.EXPENSE, request.amount(), request.date());
        t.setCategoryId(category.getId());
        t.setCardStatementId(statement.getId());
        t.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        t.setPaid(false);
        return transactions.save(t).getId();
    }

    /** Faturas do cartão com o total das compras, da mais antiga para a mais nova. */
    @Transactional(readOnly = true)
    public List<StatementSummary> statements(UUID userId, UUID cardId) {
        find(userId, cardId);
        return statements.findByUserIdAndCardIdOrderByReferenceMonthAsc(userId, cardId).stream()
                .map(s -> new StatementSummary(s.getId(), s.getCardId(), s.getReferenceMonth(), s.getClosingDate(),
                        s.getDueDate(), s.getStatus().name(), transactions.statementTotal(userId, s.getId())))
                .toList();
    }

    /** Encontra ou cria a fatura em que uma compra dessa data cai (StatementResolver). Chamar dentro de transação. */
    public CardStatement statementFor(CreditCard card, LocalDate purchaseDate) {
        StatementResolver.StatementPeriod period = StatementResolver.resolve(card.getClosingDay(), card.getDueDay(), purchaseDate);
        LocalDate reference = period.referenceMonth().atDay(1);
        statements.insertIfAbsent(card.getUserId(), card.getId(), reference, period.closingDate(), period.dueDate());
        return statements.findByUserIdAndCardIdAndReferenceMonth(card.getUserId(), card.getId(), reference).orElseThrow();
    }

    public CreditCard find(UUID userId, UUID id) {
        return cards.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Cartão não encontrado"));
    }

    Category expenseCategory(UUID userId, UUID categoryId) {
        Category category = categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
        if (category.getKind() != CategoryKind.EXPENSE) {
            throw new InvalidRequestException("categoryId", "compra no cartão precisa de uma categoria de gasto");
        }
        return category;
    }

    private void requireAccount(UUID userId, UUID accountId) {
        accounts.findByIdAndUserId(accountId, userId).orElseThrow(() -> new NotFoundException("Conta de pagamento não encontrada"));
    }

    private static CardResponse toResponse(CreditCard c) {
        return new CardResponse(c.getId(), c.getName(), c.getCreditLimit(), c.getClosingDay(), c.getDueDay(),
                c.getPaymentAccountId(), c.isArchived());
    }
}
