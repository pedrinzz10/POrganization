package com.porganization.settings;

import java.time.DateTimeException;
import java.time.ZoneId;
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

    /** Fuso do usuário; sem user_settings (ou com um fuso inválido), o padrão America/Sao_Paulo. */
    @Transactional(readOnly = true)
    public ZoneId zoneOf(UUID userId) {
        String timezone = repository.findById(userId).map(UserSettings::getTimezone)
                .orElse(UserSettings.DEFAULT_TIMEZONE);
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return ZoneId.of(UserSettings.DEFAULT_TIMEZONE);
        }
    }
}
