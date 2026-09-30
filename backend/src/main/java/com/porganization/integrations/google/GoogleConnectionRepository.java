package com.porganization.integrations.google;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface GoogleConnectionRepository extends Repository<GoogleConnection, UUID> {

    GoogleConnection save(GoogleConnection connection);

    void delete(GoogleConnection connection);

    Optional<GoogleConnection> findById(UUID userId);

    List<GoogleConnection> findAll();
}
