package com.porganization.integrations.google;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Conexão de um usuário com o Google Calendar. Os tokens só existem cifrados (TokenCrypto). */
@Entity
@Table(name = "google_connections")
public class GoogleConnection {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "google_email")
    private String googleEmail;

    @Column(name = "refresh_token_enc", nullable = false)
    private String refreshTokenEnc;

    @Column(name = "access_token_enc")
    private String accessTokenEnc;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "calendar_id", nullable = false)
    private String calendarId = "primary";

    @Column(name = "scope")
    private String scope;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected GoogleConnection() {
    }

    public GoogleConnection(UUID userId) {
        this.userId = userId;
    }

    public void connected(String googleEmail, String refreshTokenEnc, String scope) {
        this.googleEmail = googleEmail;
        this.refreshTokenEnc = refreshTokenEnc;
        this.scope = scope;
    }

    public void accessToken(String accessTokenEnc, Instant expiresAt) {
        this.accessTokenEnc = accessTokenEnc;
        this.accessExpiresAt = expiresAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getGoogleEmail() {
        return googleEmail;
    }

    public String getRefreshTokenEnc() {
        return refreshTokenEnc;
    }

    public String getAccessTokenEnc() {
        return accessTokenEnc;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public String getCalendarId() {
        return calendarId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
