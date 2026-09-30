package com.porganization.finance.recurring;

import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.accounts.AccountRepository;
import com.porganization.finance.cards.CreditCardService;
import com.porganization.finance.categories.Category;
import com.porganization.finance.categories.CategoryKind;
import com.porganization.finance.categories.CategoryRepository;
import com.porganization.finance.recurring.RecurringDtos.RecurringRequest;
import com.porganization.finance.recurring.RecurringDtos.RecurringResponse;
import com.porganization.finance.transactions.TransactionType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro dos modelos fixos. Editar só afeta os meses que ainda não foram gerados. */
@Service
public class RecurringService {

    private final RecurringTransactionRepository recurring;
    private final AccountRepository accounts;
    private final CreditCardService cards;
    private final CategoryRepository categories;

    public RecurringService(RecurringTransactionRepository recurring, AccountRepository accounts, CreditCardService cards,
            CategoryRepository categories) {
        this.recurring = recurring;
        this.accounts = accounts;
        this.cards = cards;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<RecurringResponse> list(UUID userId) {
        return recurring.findByUserIdOrderByDayOfMonthAscDescriptionAsc(userId).stream().map(RecurringService::toResponse).toList();
    }

    @Transactional
    public RecurringResponse create(UUID userId, RecurringRequest request) {
        RecurringTransaction r = new RecurringTransaction(userId);
        apply(userId, r, request);
        return toResponse(recurring.save(r));
    }

    @Transactional
    public RecurringResponse update(UUID userId, UUID id, RecurringRequest request) {
        RecurringTransaction r = find(userId, id);
        apply(userId, r, request);
        return toResponse(r);
    }

    /** Exclui o modelo; os lançamentos já gerados ficam (perdem só o vínculo). */
    @Transactional
    public void delete(UUID userId, UUID id) {
        recurring.delete(find(userId, id));
    }

    private RecurringTransaction find(UUID userId, UUID id) {
        return recurring.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Recorrente não encontrado"));
    }

    private void apply(UUID userId, RecurringTransaction r, RecurringRequest request) {
        if (request.type() == TransactionType.TRANSFER) {
            throw new InvalidRequestException("type", "recorrente é renda ou gasto");
        }
        if ((request.accountId() == null) == (request.cardId() == null)) {
            throw new InvalidRequestException("accountId", "informe uma conta ou um cartão");
        }
        if (request.cardId() != null) {
            if (request.type() != TransactionType.EXPENSE) {
                throw new InvalidRequestException("cardId", "no cartão só entra gasto");
            }
            cards.find(userId, request.cardId());
        } else {
            accounts.findByIdAndUserId(request.accountId(), userId)
                    .orElseThrow(() -> new NotFoundException("Conta não encontrada"));
        }
        Category category = categories.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
        CategoryKind expected = request.type() == TransactionType.INCOME ? CategoryKind.INCOME : CategoryKind.EXPENSE;
        if (category.getKind() != expected) {
            throw new InvalidRequestException("categoryId",
                    expected == CategoryKind.EXPENSE ? "gasto precisa de uma categoria de gasto" : "renda precisa de uma categoria de renda");
        }
        if (request.endMonth() != null && request.endMonth().isBefore(request.startMonth())) {
            throw new InvalidRequestException("endMonth", "o fim não pode ser antes do início");
        }
        String description = request.description() == null || request.description().isBlank() ? null : request.description().trim();
        r.update(request.type(), request.amount(), description, request.accountId(), request.cardId(), category.getId(),
                request.dayOfMonth(), request.startMonth(), request.endMonth());
    }

    private static RecurringResponse toResponse(RecurringTransaction r) {
        return new RecurringResponse(r.getId(), r.getType(), r.getAmount(), r.getDescription(), r.getAccountId(), r.getCardId(),
                r.getCategoryId(), r.getDayOfMonth(), r.getStartMonth(), r.getEndMonth());
    }
}
