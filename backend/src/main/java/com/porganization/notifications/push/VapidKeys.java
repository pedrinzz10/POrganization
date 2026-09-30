package com.porganization.notifications.push;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chaves VAPID do servidor (gere com `npx web-push generate-vapid-keys`): a pública vai para o
 * navegador assinar; a privada assina cada envio. subject é um contato (mailto: ou https:).
 */
@Component
public class VapidKeys {

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public VapidKeys(@Value("${porganization.push.vapid-public-key:}") String publicKey,
            @Value("${porganization.push.vapid-private-key:}") String privateKey,
            @Value("${porganization.push.vapid-subject:}") String subject) {
        this.publicKey = publicKey.trim();
        this.privateKey = privateKey.trim();
        this.subject = subject.trim();
    }

    public boolean configured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }

    public String publicKey() {
        return publicKey;
    }

    public String privateKey() {
        return privateKey;
    }

    public String subject() {
        return subject;
    }
}
