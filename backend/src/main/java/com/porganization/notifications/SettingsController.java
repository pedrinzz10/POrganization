package com.porganization.notifications;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.porganization.common.InvalidRequestException;
import com.porganization.security.CurrentUser;
import com.porganization.settings.UserSettings;
import com.porganization.settings.UserSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preferências do usuário: fuso, canais, lembrete padrão dos compromissos e horário do resumo diário. */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final UserSettingsService userSettings;

    public SettingsController(UserSettingsService userSettings) {
        this.userSettings = userSettings;
    }

    /**
     * @param defaultReminderMinutes lembrete que todo compromisso novo ganha; null = nenhum
     * @param digestTime             horário do resumo diário; null = sem resumo
     */
    public record SettingsResponse(String email, String timezone, Set<ChannelType> channels, Integer defaultReminderMinutes,
            @JsonFormat(pattern = "HH:mm") LocalTime digestTime) {
    }

    public record SettingsRequest(
            @NotBlank String timezone,
            @NotEmpty Set<ChannelType> channels,
            @Min(0) @Max(40320) Integer defaultReminderMinutes,
            @JsonFormat(pattern = "HH:mm") LocalTime digestTime) {
    }

    @GetMapping
    public SettingsResponse get(@CurrentUser UUID userId) {
        return toResponse(userSettings.settingsOf(userId));
    }

    @PutMapping
    @Transactional
    public SettingsResponse update(@CurrentUser UUID userId, @Valid @RequestBody SettingsRequest request) {
        try {
            ZoneId.of(request.timezone());
        } catch (DateTimeException e) {
            throw new InvalidRequestException("timezone", "fuso horário desconhecido");
        }
        UserSettings settings = userSettings.settingsOf(userId);
        settings.setTimezone(request.timezone());
        settings.setNotifications(request.channels().stream().map(Enum::name).sorted().toList(),
                request.defaultReminderMinutes(), request.digestTime());
        return toResponse(settings);
    }

    private static SettingsResponse toResponse(UserSettings s) {
        Set<ChannelType> channels = EnumSet.noneOf(ChannelType.class);
        s.getNotifyChannels().forEach(c -> channels.add(ChannelType.valueOf(c)));
        return new SettingsResponse(s.getEmail(), s.getTimezone(), channels, s.getDefaultReminderMinutes(), s.getDigestTime());
    }
}
