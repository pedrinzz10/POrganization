package com.porganization.notifications.push;

import com.porganization.notifications.ChannelType;
import com.porganization.notifications.Notification;
import com.porganization.notifications.NotificationChannel;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.Map;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Envia avisos por Web Push (VAPID) para todos os navegadores em que o usuário ativou as
 * notificações. O payload segue o formato do service worker do Angular: clicar na notificação
 * abre (ou foca) o app na url do aviso. Assinatura que o push service dá como expirada (404/410)
 * é apagada.
 */
@Component("pushChannel")
public class PushChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(PushChannel.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushSubscriptionRepository subscriptions;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final VapidKeys vapid;
    private volatile PushService pushService;

    public PushChannel(PushSubscriptionRepository subscriptions, TransactionTemplate transactions, ObjectMapper json,
            VapidKeys vapid) {
        this.subscriptions = subscriptions;
        this.transactions = transactions;
        this.json = json;
        this.vapid = vapid;
    }

    @Override
    public ChannelType type() {
        return ChannelType.PUSH;
    }

    @Override
    public void send(Notification notification) throws Exception {
        List<PushSubscription> targets = subscriptions.findByUserId(notification.userId());
        if (targets.isEmpty()) {
            log.info("Usuário {} sem navegador inscrito para push; nada a enviar", notification.userId());
            return;
        }
        String payload = payload(notification);
        int delivered = 0;
        Exception lastFailure = null;
        for (PushSubscription target : targets) {
            try {
                HttpResponse response = service().send(new nl.martijndwars.webpush.Notification(
                        new Subscription(target.getEndpoint(), new Subscription.Keys(target.getP256dh(), target.getAuth())),
                        payload), Encoding.AES128GCM);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    // O navegador cancelou a assinatura ou ela expirou: não adianta tentar de novo
                    transactions.executeWithoutResult(tx -> subscriptions.findByEndpoint(target.getEndpoint())
                            .ifPresent(subscriptions::delete));
                    log.info("Assinatura de push expirada ({}) removida: usuário {}", status, notification.userId());
                } else if (status >= 200 && status < 300) {
                    delivered++;
                } else {
                    String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
                    lastFailure = new IllegalStateException("push service respondeu " + status + ": " + body);
                }
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        if (delivered == 0 && lastFailure != null) {
            throw lastFailure;
        }
    }

    /**
     * Formato do ngsw-worker: notification.data.onActionClick.default abre ou foca o app na url.
     * https://angular.dev/ecosystem/service-workers/push-notifications
     */
    String payload(Notification notification) {
        Map<String, Object> body = Map.of("notification", Map.of(
                "title", notification.subject(),
                // Notificação é curta: as duas primeiras linhas (no lembrete, dia/hora e local)
                "body", String.join(" · ", notification.lines().subList(0, Math.min(2, notification.lines().size()))),
                "icon", "/favicon.ico",
                "tag", notification.kind().name().toLowerCase() + ":" + notification.subject(),
                "data", Map.of("url", notification.url(), "onActionClick", Map.of(
                        "default", Map.of("operation", "navigateLastFocusedOrOpen", "url", notification.url())))));
        return json.writeValueAsString(body);
    }

    private PushService service() throws GeneralSecurityException {
        if (!vapid.configured()) {
            throw new IllegalStateException("VAPID_PUBLIC_KEY e VAPID_PRIVATE_KEY não configuradas");
        }
        PushService current = pushService;
        if (current == null) {
            current = new PushService(vapid.publicKey(), vapid.privateKey(), vapid.subject().isEmpty() ? null : vapid.subject());
            pushService = current;
        }
        return current;
    }
}
