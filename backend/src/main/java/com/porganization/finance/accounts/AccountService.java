package com.porganization.finance.accounts;

import com.porganization.common.ConflictException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.accounts.AccountDtos.AccountRequest;
import com.porganization.finance.accounts.AccountDtos.AccountResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(UUID userId, boolean includeArchived) {
        return accounts.findByUserIdOrderByNameAsc(userId).stream()
                .filter(a -> includeArchived || !a.isArchived())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID userId, UUID id) {
        return toResponse(find(userId, id));
    }

    @Transactional
    public AccountResponse create(UUID userId, AccountRequest request) {
        return toResponse(accounts.save(new Account(userId, request.name().trim(), request.type(), request.initialBalance())));
    }

    @Transactional
    public AccountResponse update(UUID userId, UUID id, AccountRequest request) {
        Account account = find(userId, id);
        account.setName(request.name().trim());
        account.setType(request.type());
        account.setInitialBalance(request.initialBalance());
        return toResponse(account);
    }

    @Transactional
    public AccountResponse setArchived(UUID userId, UUID id, boolean archived) {
        Account account = find(userId, id);
        account.setArchived(archived);
        return toResponse(account);
    }

    /** Conta com transações não pode ser excluída (o banco barra pela FK): 409, arquive em vez disso. */
    @Transactional
    public void delete(UUID userId, UUID id) {
        accounts.delete(find(userId, id));
        try {
            accounts.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Esta conta tem lançamentos e não pode ser excluída. Arquive-a em vez disso.");
        }
    }

    public Account find(UUID userId, UUID id) {
        return accounts.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Conta não encontrada"));
    }

    private AccountResponse toResponse(Account a) {
        return new AccountResponse(a.getId(), a.getName(), a.getType(), a.getInitialBalance(), balanceOf(a), a.isArchived());
    }

    /** Saldo atual = saldo inicial + transações efetivadas. As transações chegam na F03. */
    BigDecimal balanceOf(Account account) {
        return account.getInitialBalance();
    }
}
