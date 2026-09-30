package com.porganization.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** Lembrete de um compromisso: minutesBefore antes de cada ocorrência, pelos canais escolhidos. */
@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "commitment_id", nullable = false, updatable = false)
    private UUID commitmentId;

    @Column(name = "minutes_before", nullable = false)
    private int minutesBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channels", nullable = false)
    private Set<ChannelType> channels = EnumSet.noneOf(ChannelType.class);

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Reminder() {
    }

    public Reminder(UUID userId, UUID commitmentId, int minutesBefore, Set<ChannelType> channels) {
        this.userId = userId;
        this.commitmentId = commitmentId;
        this.minutesBefore = minutesBefore;
        this.channels = EnumSet.copyOf(channels);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCommitmentId() {
        return commitmentId;
    }

    public int getMinutesBefore() {
        return minutesBefore;
    }

    public Set<ChannelType> getChannels() {
        return channels;
    }
}
