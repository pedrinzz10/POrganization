package com.porganization.settings;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

    // Na primeira vez cria com o timezone padrão; depois só atualiza o e-mail (se o token trouxer um)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into user_settings (user_id, email, timezone)
            values (:userId, :email, :timezone)
            on conflict (user_id) do update
            set email = coalesce(excluded.email, user_settings.email)
            """, nativeQuery = true)
    void upsert(UUID userId, String email, String timezone);

    /** Quem quer o resumo diário. */
    List<UserSettings> findByDigestTimeIsNotNull();

    /** Marca o resumo do dia como enviado; 0 se ele já saiu hoje (atômico: dois crons não duplicam). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            update user_settings set last_digest_date = :day
            where user_id = :userId and (last_digest_date is null or last_digest_date < :day)
            """, nativeQuery = true)
    int claimDigest(UUID userId, LocalDate day);

    /** Desfaz a marcação quando nenhum canal conseguiu entregar: o próximo cron tenta de novo. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "update user_settings set last_digest_date = :previous where user_id = :userId and last_digest_date = :day",
            nativeQuery = true)
    int releaseDigest(UUID userId, LocalDate day, LocalDate previous);
}
