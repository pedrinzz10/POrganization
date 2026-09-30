package com.porganization.finance.transactions;

import com.porganization.common.InvalidRequestException;
import com.porganization.common.NotFoundException;
import com.porganization.finance.accounts.AccountRepository;
import com.porganization.finance.transactions.TransactionDtos.TransferRequest;
import com.porganization.finance.transactions.TransactionDtos.TransferResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transferência entre contas: duas pernas TRANSFER (saída na origem, entrada no destino) com o
 * mesmo grupo, criadas e alteradas juntas na mesma transação do banco. Não contam como renda
 * nem gasto; só mudam o saldo das duas contas.
 */
@Service
public class TransferService {

    private final TransactionRepository transactions;
    private final AccountRepository accounts;

    public TransferService(TransactionRepository transactions, AccountRepository accounts) {
        this.transactions = transactions;
        this.accounts = accounts;
    }

    @Transactional
    public TransferResponse create(UUID userId, TransferRequest request) {
        validate(userId, request);
        UUID groupId = UUID.randomUUID();
        transactions.save(leg(userId, request, groupId, TransferDirection.OUT));
        transactions.save(leg(userId, request, groupId, TransferDirection.IN));
        return new TransferResponse(groupId, request.fromAccountId(), request.toAccountId(), request.amount(), request.date(),
                description(request));
    }

    @Transactional
    public TransferResponse update(UUID userId, UUID groupId, TransferRequest request) {
        validate(userId, request);
        List<Transaction> legs = legs(userId, groupId);
        for (Transaction leg : legs) {
            leg.setAmount(request.amount());
            leg.setDate(request.date());
            leg.setDescription(description(request));
            leg.setAccountId(leg.getTransferDirection() == TransferDirection.OUT ? request.fromAccountId() : request.toAccountId());
        }
        return new TransferResponse(groupId, request.fromAccountId(), request.toAccountId(), request.amount(), request.date(),
                description(request));
    }

    @Transactional
    public void delete(UUID userId, UUID groupId) {
        legs(userId, groupId).forEach(transactions::delete);
    }

    private List<Transaction> legs(UUID userId, UUID groupId) {
        List<Transaction> legs = transactions.findByUserIdAndTransferGroupId(userId, groupId);
        if (legs.isEmpty()) {
            throw new NotFoundException("Transferência não encontrada");
        }
        return legs;
    }

    private void validate(UUID userId, TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new InvalidRequestException("toAccountId", "a conta de destino precisa ser diferente da de origem");
        }
        for (UUID accountId : List.of(request.fromAccountId(), request.toAccountId())) {
            accounts.findByIdAndUserId(accountId, userId).orElseThrow(() -> new NotFoundException("Conta não encontrada"));
        }
    }

    private static Transaction leg(UUID userId, TransferRequest request, UUID groupId, TransferDirection direction) {
        Transaction leg = new Transaction(userId, TransactionType.TRANSFER, request.amount(), request.date());
        leg.setTransferLeg(groupId, direction);
        leg.setAccountId(direction == TransferDirection.OUT ? request.fromAccountId() : request.toAccountId());
        leg.setDescription(description(request));
        leg.setPaid(true);
        return leg;
    }

    private static String description(TransferRequest request) {
        return request.description() == null || request.description().isBlank() ? "Transferência" : request.description().trim();
    }
}
