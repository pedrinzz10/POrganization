package com.porganization.integrations.google;

import com.porganization.common.InvalidRequestException;
import com.porganization.integrations.google.GoogleOAuthService.Status;
import com.porganization.security.CurrentUser;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conexão com o Google Calendar. O botão "Conectar" do app busca /auth-url e navega para o Google;
 * o Google volta no /callback (público: quem identifica o usuário é o state assinado), que redireciona
 * para as Configurações do app com ?google=ok ou ?google=erro.
 */
@RestController
@RequestMapping("/api/integrations/google")
public class GoogleOAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthController.class);

    private final GoogleOAuthService service;
    private final String appUrl;

    public GoogleOAuthController(GoogleOAuthService service, @Value("${porganization.app-url}") String appUrl) {
        this.service = service;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    @GetMapping
    public Status status(@CurrentUser UUID userId) {
        return service.status(userId);
    }

    @GetMapping("/auth-url")
    public Map<String, String> authUrl(@CurrentUser UUID userId) {
        return Map.of("url", service.authUrl(userId));
    }

    /** Volta do Google. State inválido → 400; recusa ou falha na troca do code → Configurações com ?google=erro. */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (state == null) {
            throw new InvalidRequestException("state", "state ausente");
        }
        if (error != null || code == null) {
            service.verifyState(state);
            return backToApp("erro");
        }
        try {
            service.connect(code, state);
            return backToApp("ok");
        } catch (InvalidRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Falha ao conectar o Google Calendar: {}", e.getMessage());
            return backToApp("erro");
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(@CurrentUser UUID userId) {
        service.disconnect(userId);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Void> backToApp(String result) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(appUrl + "/configuracoes?google=" + result)).build();
    }
}
