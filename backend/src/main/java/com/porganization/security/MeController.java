package com.porganization.security;

import com.porganization.settings.UserSettings;
import com.porganization.settings.UserSettingsService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserSettingsService userSettingsService;

    public MeController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @GetMapping
    public MeResponse me(@CurrentUser UUID userId, @AuthenticationPrincipal Jwt jwt) {
        UserSettings settings = userSettingsService.ensureExists(userId, jwt.getClaimAsString("email"));
        return new MeResponse(settings.getUserId(), settings.getEmail(), settings.getTimezone());
    }

    public record MeResponse(UUID userId, String email, String timezone) {
    }
}
