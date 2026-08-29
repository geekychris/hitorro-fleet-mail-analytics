/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.entities.Watermark;
import com.hitorro.fleet.mailanalytics.ingest.BackfillJob;
import com.hitorro.fleet.mailanalytics.ingest.IngestOrchestrator;
import com.hitorro.fleet.mailanalytics.ingest.MailSource;
import com.hitorro.fleet.mailanalytics.ingest.MailSourceRegistry;
import com.hitorro.fleet.mailanalytics.ingest.WatermarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** REST surface for ingest sources — status, one-off run, backfill. */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final MailSourceRegistry registry;
    private final IngestOrchestrator orchestrator;
    private final BackfillJob backfill;
    private final WatermarkService watermarks;

    public IngestController(MailSourceRegistry registry,
                            IngestOrchestrator orchestrator,
                            BackfillJob backfill,
                            WatermarkService watermarks) {
        this.registry = registry;
        this.orchestrator = orchestrator;
        this.backfill = backfill;
        this.watermarks = watermarks;
    }

    @GetMapping("/sources")
    public List<Map<String, Object>> list() {
        return registry.all().stream().map(this::sourceStatus).toList();
    }

    @GetMapping("/sources/{id}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String id) {
        MailSource s = registry.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(sourceStatus(s));
    }

    @PostMapping("/sources/{id}/run")
    public ResponseEntity<Map<String, Object>> runOnce(@PathVariable String id) {
        MailSource s = registry.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        IngestOrchestrator.Batch b = orchestrator.runOnce(s);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", id);
        out.put("rowsWritten", b.rowsWritten());
        out.put("batchPath", b.batchPath() == null ? null : b.batchPath().toString());
        return ResponseEntity.ok(out);
    }

    @PostMapping("/sources/{id}/backfill")
    public ResponseEntity<Map<String, Object>> backfill(@PathVariable String id,
                                                        @RequestParam(required = false) Long daysBack) {
        MailSource s = registry.get(id);
        if (s == null) return ResponseEntity.notFound().build();
        Duration d = daysBack == null ? null : Duration.ofDays(daysBack);
        backfill.start(id, d);
        return ResponseEntity.accepted().body(Map.of(
                "sourceId", id,
                "status", "started",
                "daysBack", daysBack == null ? "default" : daysBack));
    }

    private Map<String, Object> sourceStatus(MailSource s) {
        Watermark w = watermarks.getOrCreate(s.id());
        MailSource.Health h = s.health();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id());
        out.put("kind", s.kind());
        out.put("healthy", h.ok());
        out.put("healthDetail", h.detail());
        out.put("lastRowId", w.getLastRowId());
        out.put("lastUid", w.getLastUid());
        out.put("lastRunAt", w.getLastRunAt());
        out.put("totalIngested", w.getTotalIngested());
        out.put("backfillComplete", w.isBackfillComplete());
        out.put("backfillHorizonAt", w.getBackfillHorizonAt());
        out.put("lastError", w.getLastError());
        return out;
    }
}
