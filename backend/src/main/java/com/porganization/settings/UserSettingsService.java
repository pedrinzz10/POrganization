package com.porganization.settings;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSettingsService {

    private final UserSettingsRepository repository;

    public UserSettingsService(UserSettingsRepository repository) {
        this.repository = repository;
    }

    /**
     * Garante a linha de user_settings do usuário (criada no primeiro acesso) e mantém o e-mail
     * em dia com o do token. Upsert atômico: dois primeiros acessos simultâneos não duplicam.
     */
    @Transactional
    public UserSettings ensureExists(UUID userId, String email) {
        repository.upsert(userId, email, UserSettings.DEFAULT_TIMEZONE);
        return repository.findById(userId).orElseThrow();
    }
}
