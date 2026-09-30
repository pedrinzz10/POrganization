package com.porganization.notifications.push;

import com.porganization.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assinaturas de Web Push do usuário. O corpo do POST é o PushSubscription.toJSON() do navegador:
 * {endpoint, keys: {p256dh, auth}}. O DELETE recebe só {endpoint}.
 */
@RestController
@RequestMapping("/api/push")
public class PushSubscriptionController {

    private final PushSubscriptionRepository subscriptions;
    private final VapidKeys vapid;

    public PushSubscriptionController(PushSubscriptionRepository subscriptions, VapidKeys vapid) {
        this.subscriptions = subscriptions;
        this.vapid = vapid;
    }

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }

    public record SubscriptionRequest(
            @NotBlank @Size(max = 2000) @Pattern(regexp = "https://.+", message = "deve ser uma URL https") String endpoint,
            @NotNull @Valid Keys keys) {
    }

    public record EndpointRequest(@NotBlank String endpoint) {
    }

    public record VapidKey(String publicKey) {
    }

    /** Chave pública VAPID que o navegador usa para assinar (applicationServerKey). null = push desligado. */
    @GetMapping("/vapid-public-key")
    public VapidKey vapidPublicKey() {
        return new VapidKey(vapid.configured() ? vapid.publicKey() : null);
    }

    @PostMapping("/subscriptions")
    @Transactional
    public ResponseEntity<Void> subscribe(@CurrentUser UUID userId, @Valid @RequestBody SubscriptionRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        String agent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 300));
        subscriptions.findByEndpoint(request.endpoint()).ifPresentOrElse(
                existing -> existing.update(userId, request.keys().p256dh(), request.keys().auth(), agent),
                () -> subscriptions.save(new PushSubscription(userId, request.endpoint(), request.keys().p256dh(),
                        request.keys().auth(), agent)));
        return ResponseEntity.noContent().build();
    }

    /** Desativar neste navegador. Endpoint que não é do usuário (ou já removido) também responde 204. */
    @DeleteMapping("/subscriptions")
    @Transactional
    public ResponseEntity<Void> unsubscribe(@CurrentUser UUID userId, @Valid @RequestBody EndpointRequest request) {
        subscriptions.findByEndpointAndUserId(request.endpoint(), userId).ifPresent(subscriptions::delete);
        return ResponseEntity.noContent().build();
    }
}
