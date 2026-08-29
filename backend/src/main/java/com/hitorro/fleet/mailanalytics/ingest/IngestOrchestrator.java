/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.entities.Watermark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Reads delta from a {@link MailSource}, writes an NDJSON batch under
 * {@code mailanalytics.pipelines.hot-dir/{source}/inbox/}, triggers the
 * enrichment pipeline, advances the watermark. Failures hold the
 * watermark so the next run retries the same range.
 */
@Service
public class IngestOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(IngestOrchestrator.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MailAnalyticsProperties props;
    private final WatermarkService watermarks;
    private final PipelineTrigger trigger;
    private final ObjectMapper mapper = new ObjectMapper();

    public IngestOrchestrator(MailAnalyticsProperties props,
                              WatermarkService watermarks,
                              PipelineTrigger trigger) {
        this.props = props;
        this.watermarks = watermarks;
        this.trigger = trigger;
    }

    /** Fetch one batch, write NDJSON, trigger pipeline, advance watermark.
     *  Returns {@code (rowsWritten, batchPath|null)}. */
    public Batch runOnce(MailSource source) {
        Watermark wm = watermarks.getOrCreate(source.id());
        int batchSize = props.getIngest().getBatchSize();
        Path outDir = ensureDir(Path.of(props.getPipelines().getHotDir(), source.id(), "inbox"));
        Path batchPath = outDir.resolve("batch-" + LocalDateTime.now().format(TS) + ".ndjson");
        AtomicInteger written = new AtomicInteger();
        long maxCursor = wm.getLastRowId() == null ? 0L : wm.getLastRowId();
        try (Stream<RawMail> s = source.fetchSince(wm, batchSize);
             BufferedWriter w = Files.newBufferedWriter(batchPath)) {
            var it = s.iterator();
            while (it.hasNext()) {
                RawMail m = it.next();
                w.write(mapper.writeValueAsString(toJson(m)));
                w.newLine();
                written.incrementAndGet();
                if (m.sourceCursor() > maxCursor) maxCursor = m.sourceCursor();
            }
        } catch (IOException e) {
            watermarks.markError(source.id(), "ndjson write failed: " + e.getMessage());
            log.warn("IngestOrchestrator[{}] ndjson write failed: {}", source.id(), e.getMessage());
            return new Batch(0, null);
        } catch (RuntimeException e) {
            watermarks.markError(source.id(), e.getMessage());
            log.warn("IngestOrchestrator[{}] fetch failed: {}", source.id(), e.getMessage());
            return new Batch(0, null);
        }

        if (written.get() == 0) {
            try { Files.deleteIfExists(batchPath); } catch (IOException ignore) {}
            log.debug("IngestOrchestrator[{}] no new rows since rowId={}", source.id(), wm.getLastRowId());
            return new Batch(0, null);
        }

        try {
            trigger.trigger(source.id(), batchPath);
            watermarks.advance(source.id(), maxCursor, written.get());
            log.info("IngestOrchestrator[{}] wrote {} rows, watermark advanced to {}",
                    source.id(), written.get(), maxCursor);
            return new Batch(written.get(), batchPath);
        } catch (RuntimeException e) {
            watermarks.markError(source.id(), "pipeline trigger failed: " + e.getMessage());
            log.warn("IngestOrchestrator[{}] pipeline trigger failed: {}", source.id(), e.getMessage());
            return new Batch(written.get(), batchPath);
        }
    }

    private Map<String, Object> toJson(RawMail m) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("source", m.sourceId());
        row.put("source_cursor", m.sourceCursor());
        row.put("message_id", m.messageId());
        row.put("date_received", m.dateReceived().toString());
        row.put("sender_address", m.senderAddress());
        row.put("sender_name", m.senderName());
        row.put("sender_domain", m.senderDomain());
        row.put("subject", m.subject());
        row.put("body_preview", m.bodyPreview());
        row.put("mailbox_url", m.mailboxUrl());
        row.put("flags", Map.of(
                "read", m.read(),
                "flagged", m.flagged(),
                "newsletter", m.newsletter(),
                "size_bytes", m.sizeBytes(),
                "recipient_count", m.recipientCount()));
        return row;
    }

    private Path ensureDir(Path p) {
        try { Files.createDirectories(p); } catch (IOException e) {
            throw new RuntimeException("cannot create " + p, e);
        }
        return p;
    }

    public record Batch(int rowsWritten, Path batchPath) {
        public static Batch empty() { return new Batch(0, null); }
    }
}
