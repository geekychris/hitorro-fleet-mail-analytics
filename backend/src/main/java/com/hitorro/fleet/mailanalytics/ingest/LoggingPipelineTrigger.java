/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Default trigger — logs the batch drop and returns success without
 * actually invoking the pipeline. Operator drives the mesh pipeline
 * out-of-band against the hot dir. Right for dev.
 */
@Component
@ConditionalOnProperty(name = "mailanalytics.pipeline.trigger", havingValue = "logging", matchIfMissing = true)
public class LoggingPipelineTrigger implements PipelineTrigger {

    private static final Logger log = LoggerFactory.getLogger(LoggingPipelineTrigger.class);

    @Override
    public Result trigger(String sourceId, Path ndjsonBatch) {
        log.info("[ingest] pipeline batch ready — sourceId={} batch={} (operator: run mesh mail-enrich-from-ndjson pipeline against hot dir)",
                sourceId, ndjsonBatch);
        return new Result(true, "logged", "no-op — logging trigger");
    }
}
