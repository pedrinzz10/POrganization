package com.porganization.finance.transactions;

import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.accounts.AccountRepository;
import com.porganization.finance.categories.Category;
import com.porganization.finance.categories.CategoryKind;
import com.porganization.finance.categories.CategoryRepository;
import com.porganization.finance.categories.FinanceTag;
import com.porganization.finance.categories.FinanceTagRepository;
import com.porganization.finance.transactions.TransactionDtos.MonthSummary;
import com.porganization.finance.transactions.TransactionDtos.TagRef;
import com.porganization.finance.transactions.TransactionDtos.TransactionRequest;
import com.porganization.finance.transactions.TransactionDtos.TransactionResponse;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;
    private final CategoryRepository categories;
    private final FinanceTagRepository tags;

    public TransactionService(TransactionRepository transactions, AccountRepository accounts,
            CategoryRepository categories, FinanceTagRepository tags) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.categories = categories;
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(UUID userId, YearMonth month, UUID accountId, UUID categoryId, UUID tagId,
            TransactionType type) {
        List<Transaction> found = transactions.findAll(
                TransactionSpecifications.of(userId, month, accountId, categoryId, tagId, type),
                Sort.by(Sort.Order.desc("date"), Sort.Order.desc("createdAt")));
        return toResponses(userId, found);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID userId, UUID id) {
        return toResponses(userId, List.of(find(userId, id))).getFirst();
    }

    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest request) {
        Transaction transaction = new Transaction(userId, request.type(), request.amount(), request.date());
        apply(userId, transaction, request);
        return toResponses(userId, List.of(transactions.save(transaction))).getFirst();
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID id, TransactionRequest request) {
        Transaction transaction = find(userId, id);
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setDate(request.date());
        apply(userId, transaction, request);
        return toResponses(userId, List.of(transaction)).getFirst();
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        transactions.delete(find(userId, id));
    }

    /** Renda e gasto do mês, pagos ou não. Transferências não contam (F04). */
    @Transactional(readOnly = true)
    public MonthSummary summary(UUID userId, YearMonth month) {
        BigDecimal income = transactions.totalOf(userId, TransactionType.INCOME, month.atDay(1), month.atEndOfMonth());
        BigDecimal expense = transactions.totalOf(userId, TransactionType.EXPENSE, month.atDay(1), month.atEndOfMonth());
        return new MonthSummary(month.toString(), income, expense, income.subtract(expense));
    }

    public Transaction find(UUID userId, UUID id) {
        return transactions.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Transação não encontrada"));
    }

    private void apply(UUID userId, Transaction transaction, TransactionRequest request) {
        if (request.type() == TransactionType.TRANSFER) {
            throw new InvalidRequestException("type", "use /api/finance/transfers para transferências");
        }
        accounts.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
        Category category = categories.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
        CategoryKind expected = request.type() == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        if (category.getKind() != expected) {
            throw new InvalidRequestException("categoryId",
                    expected == CategoryKind.EXPENSE ? "gasto precisa de uma categoria de gasto" : "renda precisa de uma categoria de renda");
        }
        transaction.setAccountId(request.accountId());
        transaction.setCategoryId(category.getId());
        transaction.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        transaction.setPaid(request.paid() == null || request.paid());
        transaction.getTags().clear();
        transaction.getTags().addAll(resolveTags(userId, request.tagIds()));
    }

    private List<FinanceTag> resolveTags(UUID userId, List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> wanted = new LinkedHashSet<>(tagIds);
        List<FinanceTag> found = tags.findByUserIdAndIdIn(userId, wanted);
        if (found.size() != wanted.size()) {
            throw new InvalidRequestException("tagIds", "tem tag que não existe ou não é sua");
        }
        return found;
    }

    /** Monta as respostas com os nomes de conta e categoria (uma consulta de cada, não uma por linha). */
    List<TransactionResponse> toResponses(UUID userId, List<Transaction> list) {
        Map<UUID, String> accountNames = accounts.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(a -> a.getId(), a -> a.getName()));
        Map<UUID, String> categoryNames = categories.findByUserIdOrderByKindAscNameAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        return list.stream()
                .map(t -> new TransactionResponse(t.getId(), t.getType(), t.getAmount(), t.getDate(), t.getDescription(),
                        t.getAccountId(), accountNames.get(t.getAccountId()), t.getCategoryId(),
                        categoryNames.get(t.getCategoryId()), t.isPaid(),
                        t.getTags().stream().sorted(Comparator.comparing(FinanceTag::getName)).map(g -> new TagRef(g.getId(), g.getName())).toList(),
                        t.getCardStatementId(), t.getPurchaseId(), t.getInstallmentNumber(), t.getInstallmentCount()))
                .toList();
    }
}
