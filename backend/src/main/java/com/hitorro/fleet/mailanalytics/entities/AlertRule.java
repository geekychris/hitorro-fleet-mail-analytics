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
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Attaches a schedule, delta rule, and delivery channels to a
 * {@link SavedQuery}. The engine runs the saved query on the cron,
 * compares to {@code lastFingerprint}, and dispatches when the delta
 * rule says fire (subject to cooldown / mute).
 */
@Entity
@Table(name = "alert_rule")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saved_query_id", nullable = false)
    private Long savedQueryId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 120)
    private String cron;

    @Enumerated(EnumType.STRING)
    @Column(name = "delta_mode", nullable = false, length = 64)
    private AlertDeltaMode deltaMode = AlertDeltaMode.ANY_NEW;

    @Lob
    @Column(name = "threshold_config_json")
    private String thresholdConfigJson;

    /** JSON array of {channel, target, config} triples. */
    @Lob
    @Column(name = "delivery_channels_json", nullable = false)
    private String deliveryChannelsJson;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "cooldown_seconds", nullable = false)
    private int cooldownSeconds = 0;

    @Column(name = "muted_until")
    private Instant mutedUntil;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    @Column(name = "last_fingerprint", length = 128)
    private String lastFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSavedQueryId() { return savedQueryId; }
    public void setSavedQueryId(Long savedQueryId) { this.savedQueryId = savedQueryId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public AlertDeltaMode getDeltaMode() { return deltaMode; }
    public void setDeltaMode(AlertDeltaMode deltaMode) { this.deltaMode = deltaMode; }
    public String getThresholdConfigJson() { return thresholdConfigJson; }
    public void setThresholdConfigJson(String thresholdConfigJson) { this.thresholdConfigJson = thresholdConfigJson; }
    public String getDeliveryChannelsJson() { return deliveryChannelsJson; }
    public void setDeliveryChannelsJson(String deliveryChannelsJson) { this.deliveryChannelsJson = deliveryChannelsJson; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }
    public Instant getMutedUntil() { return mutedUntil; }
    public void setMutedUntil(Instant mutedUntil) { this.mutedUntil = mutedUntil; }
    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastFingerprint() { return lastFingerprint; }
    public void setLastFingerprint(String lastFingerprint) { this.lastFingerprint = lastFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
