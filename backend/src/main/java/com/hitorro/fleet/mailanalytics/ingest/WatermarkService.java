/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.hitorro.fleet.mailanalytics.entities.Watermark;
import com.hitorro.fleet.mailanalytics.repo.WatermarkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Read/write watermark rows. All writes are transactional. */
@Service
public class WatermarkService {

    private final WatermarkRepository repo;

    public WatermarkService(WatermarkRepository repo) { this.repo = repo; }

    public Watermark getOrCreate(String sourceId) {
        return repo.findById(sourceId).orElseGet(() -> {
            Watermark w = new Watermark();
            w.setSourceId(sourceId);
            return repo.save(w);
        });
    }

    @Transactional
    public void advance(String sourceId, long lastRowId, int ingested) {
        Watermark w = getOrCreate(sourceId);
        w.setLastRowId(Math.max(lastRowId, w.getLastRowId() == null ? 0 : w.getLastRowId()));
        w.setLastRunAt(Instant.now());
        w.setTotalIngested(w.getTotalIngested() + ingested);
        w.setLastError(null);
        repo.save(w);
    }

    @Transactional
    public void advanceImap(String sourceId, long uidValidity, long lastUid, int ingested) {
        Watermark w = getOrCreate(sourceId);
        w.setLastUidValidity(uidValidity);
        w.setLastUid(lastUid);
        w.setLastRunAt(Instant.now());
        w.setTotalIngested(w.getTotalIngested() + ingested);
        w.setLastError(null);
        repo.save(w);
    }

    @Transactional
    public void markError(String sourceId, String error) {
        Watermark w = getOrCreate(sourceId);
        w.setLastError(error);
        w.setLastRunAt(Instant.now());
        repo.save(w);
    }

    @Transactional
    public void completeBackfill(String sourceId, Instant horizon) {
        Watermark w = getOrCreate(sourceId);
        w.setBackfillHorizonAt(horizon);
        w.setBackfillComplete(true);
        repo.save(w);
    }

    @Transactional
    public void resetBackfill(String sourceId, Instant horizon) {
        Watermark w = getOrCreate(sourceId);
        w.setBackfillHorizonAt(horizon);
        w.setBackfillComplete(false);
        w.setLastError(null);
        repo.save(w);
    }
}
