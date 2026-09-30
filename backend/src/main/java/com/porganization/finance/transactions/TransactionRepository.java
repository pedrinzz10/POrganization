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

    List<Transaction> findByUserIdAndPurchaseId(UUID userId, UUID purchaseId);

    /** Compras de uma fatura (linhas sem conta), na ordem em que aconteceram. */
    List<Transaction> findByUserIdAndCardStatementIdAndAccountIdIsNullOrderByDateAscCreatedAtAsc(UUID userId, UUID cardStatementId);

    /** O pagamento de uma fatura: a única linha da fatura que tem conta. */
    Optional<Transaction> findFirstByUserIdAndCardStatementIdAndAccountIdIsNotNull(UUID userId, UUID cardStatementId);

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

    /**
     * Total do tipo no período (renda ou gasto), pagos ou não; transferências nunca entram aqui.
     * O pagamento de fatura (conta e fatura preenchidas) fica de fora: as compras já contaram no mês delas.
     */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.userId = :userId and t.type = :type and t.date between :from and :to
              and (t.accountId is null or t.cardStatementId is null)
            """)
    BigDecimal totalOf(UUID userId, TransactionType type, LocalDate from, LocalDate to);

    /**
     * Gasto por categoria no período, pago ou não, pela data do lançamento: compra no cartão conta
     * no mês da compra, não no da fatura. O pagamento de fatura fica de fora (não é gasto novo).
     */
    @Query("""
            select new com.porganization.finance.transactions.CategoryTotal(t.categoryId, sum(t.amount))
            from Transaction t
            where t.userId = :userId and t.type = com.porganization.finance.transactions.TransactionType.EXPENSE
              and t.date between :from and :to and t.categoryId is not null
              and (t.accountId is null or t.cardStatementId is null)
            group by t.categoryId
            """)
    List<CategoryTotal> expenseByCategory(UUID userId, LocalDate from, LocalDate to);

    /** Total das compras de uma fatura (linhas sem conta; o pagamento da fatura, F07, tem conta). */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.userId = :userId and t.cardStatementId = :statementId and t.accountId is null
            """)
    BigDecimal statementTotal(UUID userId, UUID statementId);
}
