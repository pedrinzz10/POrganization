package com.porganization.settings;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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
}
