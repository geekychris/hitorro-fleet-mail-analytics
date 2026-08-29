/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One (channel, target) delivery attempt for an {@link AlertFiring}. */
@Entity
@Table(name = "alert_delivery")
public class AlertDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firing_id", nullable = false)
    private Long firingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private DeliveryChannelKind channel;

    /** Address / URL / inbox-user, per channel semantics. */
    @Column(length = 500)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFiringId() { return firingId; }
    public void setFiringId(Long firingId) { this.firingId = firingId; }
    public DeliveryChannelKind getChannel() { return channel; }
    public void setChannel(DeliveryChannelKind channel) { this.channel = channel; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
}
