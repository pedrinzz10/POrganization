package com.porganization.notifications.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** Assinatura de Web Push de um navegador: para onde mandar (endpoint) e as chaves para cifrar. */
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false, updatable = false)
    private String endpoint;

    @Column(name = "p256dh", nullable = false)
    private String p256dh;

    @Column(name = "auth", nullable = false)
    private String auth;

    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        this.userId = userId;
        this.endpoint = endpoint;
        update(userId, p256dh, auth, userAgent);
    }

    /** O mesmo navegador pode assinar de novo (chaves novas) ou trocar de usuário logado. */
    public void update(UUID userId, String p256dh, String auth, String userAgent) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public String getAuth() {
        return auth;
    }
}
