package com.porganization.finance.cards;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface CreditCardRepository extends Repository<CreditCard, UUID> {

    CreditCard save(CreditCard card);

    Optional<CreditCard> findByIdAndUserId(UUID id, UUID userId);

    List<CreditCard> findByUserIdOrderByNameAsc(UUID userId);
}
