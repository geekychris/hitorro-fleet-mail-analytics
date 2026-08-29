/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** One firing of an {@link AlertRule} — parent of {@link AlertDelivery}s. */
@Entity
@Table(name = "alert_firing")
public class AlertFiring {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_rule_id", nullable = false)
    private Long alertRuleId;

    @Column(name = "fired_at", nullable = false)
    private Instant firedAt;

    @Column(length = 128)
    private String fingerprint;

    @Lob
    @Column(name = "result_summary_json")
    private String resultSummaryJson;

    @Lob
    @Column(name = "matched_doc_ids_json")
    private String matchedDocIdsJson;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAlertRuleId() { return alertRuleId; }
    public void setAlertRuleId(Long alertRuleId) { this.alertRuleId = alertRuleId; }
    public Instant getFiredAt() { return firedAt; }
    public void setFiredAt(Instant firedAt) { this.firedAt = firedAt; }
    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }
    public String getResultSummaryJson() { return resultSummaryJson; }
    public void setResultSummaryJson(String resultSummaryJson) { this.resultSummaryJson = resultSummaryJson; }
    public String getMatchedDocIdsJson() { return matchedDocIdsJson; }
    public void setMatchedDocIdsJson(String matchedDocIdsJson) { this.matchedDocIdsJson = matchedDocIdsJson; }
}
