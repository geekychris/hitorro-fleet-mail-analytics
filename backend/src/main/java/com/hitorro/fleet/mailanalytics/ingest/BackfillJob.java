/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * One-shot historical backfill. Repeatedly calls {@link IngestOrchestrator#runOnce}
 * in a loop off the scheduler thread until an empty batch is returned or
 * the horizon is reached. UI kicks this via POST /api/ingest/sources/{id}/backfill.
 */
@Service
public class BackfillJob {

    private static final Logger log = LoggerFactory.getLogger(BackfillJob.class);

    private final MailAnalyticsProperties props;
    private final MailSourceRegistry registry;
    private final IngestOrchestrator orchestrator;
    private final WatermarkService watermarks;

    public BackfillJob(MailAnalyticsProperties props,
                       MailSourceRegistry registry,
                       IngestOrchestrator orchestrator,
                       WatermarkService watermarks) {
        this.props = props;
        this.registry = registry;
        this.orchestrator = orchestrator;
        this.watermarks = watermarks;
    }

    public CompletableFuture<Result> start(String sourceId, Duration horizon) {
        MailSource src = registry.get(sourceId);
        if (src == null) return CompletableFuture.completedFuture(Result.notFound(sourceId));
        Duration effectiveHorizon = horizon != null ? horizon : props.getIngest().getBackfillHorizon();
        Instant horizonAt = Instant.now().minus(effectiveHorizon);
        watermarks.resetBackfill(sourceId, horizonAt);
        return CompletableFuture.supplyAsync(() -> run(src, horizonAt),
                Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "ma-backfill-" + sourceId);
                    t.setDaemon(true);
                    return t;
                }));
    }

    private Result run(MailSource src, Instant horizonAt) {
        long total = 0;
        int batches = 0;
        long start = System.currentTimeMillis();
        while (true) {
            IngestOrchestrator.Batch b = orchestrator.runOnce(src);
            if (b.rowsWritten() == 0) break;
            total += b.rowsWritten();
            batches++;
            if (batches > 10_000) {
                log.warn("backfill[{}] hit hard cap 10k batches", src.id());
                break;
            }
        }
        watermarks.completeBackfill(src.id(), horizonAt);
        long elapsed = System.currentTimeMillis() - start;
        log.info("backfill[{}] done — {} rows in {} batches ({} ms)", src.id(), total, batches, elapsed);
        return new Result(src.id(), true, total, batches, elapsed, null);
    }

    public record Result(String sourceId, boolean ok, long totalRows,
                         int batches, long elapsedMs, String error) {
        static Result notFound(String id) { return new Result(id, false, 0, 0, 0, "unknown source"); }
    }
}
