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

/** One row per {@code /api/*} request. Fuel for the enrichment suggester. */
@Entity
@Table(name = "query_audit")
public class QueryAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String path;

    @Lob
    @Column(name = "query_json")
    private String queryJson;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "result_count")
    private Long resultCount;

    @Column(length = 255)
    private String caller;

    @Column(name = "recorded_at", nullable = false)
    private Instant at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getQueryJson() { return queryJson; }
    public void setQueryJson(String queryJson) { this.queryJson = queryJson; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public Long getResultCount() { return resultCount; }
    public void setResultCount(Long resultCount) { this.resultCount = resultCount; }
    public String getCaller() { return caller; }
    public void setCaller(String caller) { this.caller = caller; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
}
