package com.porganization.notifications;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints chamados pelo cron externo (cron-job.org ou pg_cron + pg_net do Supabase), a cada ~5 minutos.
 * No plano gratuito do Render a API dorme sem acesso, então um @Scheduled interno não é confiável.
 * Sem JWT: a proteção é o header X-Cron-Secret, que precisa bater com CRON_SECRET. Sem CRON_SECRET
 * configurado, tudo responde 401.
 */
@RestController
@RequestMapping("/internal")
public class InternalCronController {

    public static final String SECRET_HEADER = "X-Cron-Secret";

    private final ReminderDispatcher reminders;
    private final DailyDigestService digests;
    private final byte[] secret;

    public InternalCronController(ReminderDispatcher reminders, DailyDigestService digests,
            @Value("${porganization.cron.secret:}") String secret) {
        this.reminders = reminders;
        this.digests = digests;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Lembretes devidos e resumos diários que já deram a hora. */
    @PostMapping("/reminders/dispatch")
    public ResponseEntity<Map<String, Integer>> dispatch(@RequestHeader(value = SECRET_HEADER, required = false) String header) {
        if (!authorized(header)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ReminderDispatcher.Result result = reminders.dispatch();
        int digestsSent = digests.run();
        return ResponseEntity.ok(Map.of("sent", result.sent(), "failed", result.failed(), "digests", digestsSent));
    }

    /** Comparação em tempo constante, para não vazar o segredo pelo tempo de resposta. */
    private boolean authorized(String header) {
        return secret.length > 0 && header != null
                && MessageDigest.isEqual(secret, header.getBytes(StandardCharsets.UTF_8));
    }
}
