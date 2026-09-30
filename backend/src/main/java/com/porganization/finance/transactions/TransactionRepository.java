package com.porganization.finance.transactions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface TransactionRepository extends Repository<Transaction, UUID> {

    Transaction save(Transaction transaction);

    void delete(Transaction transaction);

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    List<Transaction> findByUserIdAndTransferGroupId(UUID userId, UUID transferGroupId);

    /** As Specifications de TransactionSpecifications sempre começam pelo dono (ownedBy). */
    List<Transaction> findAll(Specification<Transaction> spec, Sort sort);

    /** Movimento pago de uma conta: rendas e transferências recebidas somam, gastos e enviadas subtraem. */
    @Query("""
            select coalesce(sum(case when t.type = com.porganization.finance.transactions.TransactionType.INCOME then t.amount
                                     when t.type = com.porganization.finance.transactions.TransactionType.EXPENSE then -t.amount
                                     when t.transferDirection = com.porganization.finance.transactions.TransferDirection.IN then t.amount
                                     when t.transferDirection = com.porganization.finance.transactions.TransferDirection.OUT then -t.amount
                                     else 0 end), 0)
            from Transaction t where t.userId = :userId and t.accountId = :accountId and t.paid = true
            """)
    BigDecimal paidMovement(UUID userId, UUID accountId);

    /** Total do tipo no período (renda ou gasto), pagos ou não; transferências nunca entram aqui. */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.userId = :userId and t.type = :type and t.date between :from and :to
            """)
    BigDecimal totalOf(UUID userId, TransactionType type, LocalDate from, LocalDate to);
}
