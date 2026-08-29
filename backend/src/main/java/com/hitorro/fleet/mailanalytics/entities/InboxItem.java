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
import jakarta.persistence.Table;

import java.time.Instant;

/** Durable in-app inbox row — the fallback channel that always writes. */
@Entity
@Table(name = "inbox_item")
public class InboxItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firing_id")
    private Long firingId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "body_preview", length = 2000)
    private String bodyPreview;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Severity severity = Severity.INFO;

    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private boolean dismissed = false;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFiringId() { return firingId; }
    public void setFiringId(Long firingId) { this.firingId = firingId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBodyPreview() { return bodyPreview; }
    public void setBodyPreview(String bodyPreview) { this.bodyPreview = bodyPreview; }
    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public boolean isDismissed() { return dismissed; }
    public void setDismissed(boolean dismissed) { this.dismissed = dismissed; }
    public Instant getSnoozedUntil() { return snoozedUntil; }
    public void setSnoozedUntil(Instant snoozedUntil) { this.snoozedUntil = snoozedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
