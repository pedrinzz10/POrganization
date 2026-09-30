package com.porganization.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_settings")
public class UserSettings {

    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email")
    private String email;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    /** "PUSH" e/ou "EMAIL" (ChannelType), para o lembrete padrão e o resumo diário. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notify_channels", nullable = false)
    private List<String> notifyChannels = new ArrayList<>(List.of("PUSH"));

    /** Lembrete que todo compromisso novo ganha; null = nenhum. */
    @Column(name = "default_reminder_minutes")
    private Integer defaultReminderMinutes;

    /** Horário do resumo diário (fuso do usuário); null = sem resumo. */
    @Column(name = "digest_time")
    private LocalTime digestTime;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected UserSettings() {
    }

    public UserSettings(UUID userId, String email) {
        this.userId = userId;
        this.email = email;
        this.timezone = DEFAULT_TIMEZONE;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public List<String> getNotifyChannels() {
        return notifyChannels;
    }

    public Integer getDefaultReminderMinutes() {
        return defaultReminderMinutes;
    }

    public LocalTime getDigestTime() {
        return digestTime;
    }

    public void setNotifications(List<String> channels, Integer defaultReminderMinutes, LocalTime digestTime) {
        this.notifyChannels = new ArrayList<>(channels);
        this.defaultReminderMinutes = defaultReminderMinutes;
        this.digestTime = digestTime;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
