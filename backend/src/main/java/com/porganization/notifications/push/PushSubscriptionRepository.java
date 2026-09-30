package com.porganization.notifications.push;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface PushSubscriptionRepository extends Repository<PushSubscription, UUID> {

    PushSubscription save(PushSubscription subscription);

    void delete(PushSubscription subscription);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByEndpointAndUserId(String endpoint, UUID userId);

    List<PushSubscription> findByUserId(UUID userId);

    long countByUserId(UUID userId);
}
