/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Per-source ingest watermark. Primary key is the source id
 * (e.g. {@code mac-mail} or {@code primary-imap:INBOX} — IMAP folder
 * appended so each folder gets its own row).
 */
@Entity
@Table(name = "watermark")
public class Watermark {

    @Id
    @Column(name = "source_id", length = 128)
    private String sourceId;

    /** SQLite ROWID cursor. Null for IMAP. */
    @Column(name = "last_row_id")
    private Long lastRowId;

    /** IMAP UID cursor. Null for SQLite. */
    @Column(name = "last_uid")
    private Long lastUid;

    /** IMAP UIDVALIDITY. Reset UIDs if this changes on the server. */
    @Column(name = "last_uidvalidity")
    private Long lastUidValidity;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /** Oldest timestamp reached during backfill. Null before backfill starts. */
    @Column(name = "backfill_horizon_at")
    private Instant backfillHorizonAt;

    @Column(name = "backfill_complete", nullable = false)
    private boolean backfillComplete = false;

    @Column(name = "total_ingested", nullable = false)
    private long totalIngested = 0;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public Long getLastRowId() { return lastRowId; }
    public void setLastRowId(Long lastRowId) { this.lastRowId = lastRowId; }
    public Long getLastUid() { return lastUid; }
    public void setLastUid(Long lastUid) { this.lastUid = lastUid; }
    public Long getLastUidValidity() { return lastUidValidity; }
    public void setLastUidValidity(Long lastUidValidity) { this.lastUidValidity = lastUidValidity; }
    public Instant getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
    public Instant getBackfillHorizonAt() { return backfillHorizonAt; }
    public void setBackfillHorizonAt(Instant backfillHorizonAt) { this.backfillHorizonAt = backfillHorizonAt; }
    public boolean isBackfillComplete() { return backfillComplete; }
    public void setBackfillComplete(boolean backfillComplete) { this.backfillComplete = backfillComplete; }
    public long getTotalIngested() { return totalIngested; }
    public void setTotalIngested(long totalIngested) { this.totalIngested = totalIngested; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
