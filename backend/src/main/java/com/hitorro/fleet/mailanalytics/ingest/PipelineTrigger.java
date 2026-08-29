/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import java.nio.file.Path;

/**
 * Enrichment pipeline invocation strategy. Two implementations:
 * <ul>
 *   <li>{@link LoggingPipelineTrigger} (default) — writes NDJSON to the
 *       hot dir but only logs the invocation. Operator runs the mesh
 *       pipeline out-of-band. Right for dev + first bring-up.</li>
 *   <li>{@link HttpPipelineTrigger} — POSTs the ingest job YAML to a
 *       running mesh-driver's {@code /mesh/jobs/run}. Right for
 *       clustered deploys with a co-located mesh-driver.</li>
 * </ul>
 *
 * <p>Kept as a strategy so the analytics jar doesn't need to embed the
 * full mesh runtime (SQLite, JVS enrich, Lucene, RocksDB, ...).</p>
 */
public interface PipelineTrigger {

    /**
     * Enrich the given NDJSON batch. Returns when the pipeline has
     * accepted the job — not when it finishes. Failure to trigger
     * (network, driver down) throws.
     */
    Result trigger(String sourceId, Path ndjsonBatch);

    record Result(boolean accepted, String jobId, String detail) {}
}
