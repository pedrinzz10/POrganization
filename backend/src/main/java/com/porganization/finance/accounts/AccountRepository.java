package com.porganization.finance.accounts;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface AccountRepository extends Repository<Account, UUID> {

    Account save(Account account);

    void delete(Account account);

    void flush();

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    List<Account> findByUserIdOrderByNameAsc(UUID userId);
}
