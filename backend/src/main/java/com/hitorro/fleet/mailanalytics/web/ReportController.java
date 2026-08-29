/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.entities.Report;
import com.hitorro.fleet.mailanalytics.entities.ReportRun;
import com.hitorro.fleet.mailanalytics.reports.ReportEngine;
import com.hitorro.fleet.mailanalytics.reports.ReportScheduler;
import com.hitorro.fleet.mailanalytics.repo.ReportRepository;
import com.hitorro.fleet.mailanalytics.repo.ReportRunRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportRepository reports;
    private final ReportRunRepository runs;
    private final ReportEngine engine;
    private final ReportScheduler scheduler;

    public ReportController(ReportRepository reports, ReportRunRepository runs,
                            ReportEngine engine, ReportScheduler scheduler) {
        this.reports = reports;
        this.runs = runs;
        this.engine = engine;
        this.scheduler = scheduler;
    }

    @GetMapping public List<Report> list() { return reports.findAll(); }
    @GetMapping("/{id}") public Report get(@PathVariable Long id) { return reports.findById(id).orElseThrow(); }

    @PostMapping public Report create(@RequestBody Report r) {
        r.setId(null);
        Report saved = reports.save(r);
        scheduler.refresh();
        return saved;
    }

    @PutMapping("/{id}") public Report update(@PathVariable Long id, @RequestBody Report r) {
        r.setId(id);
        Report saved = reports.save(r);
        scheduler.refresh();
        return saved;
    }

    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        reports.deleteById(id);
        scheduler.refresh();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run-now")
    public ReportRun runNow(@PathVariable Long id) { return engine.runNow(id); }

    @GetMapping("/{id}/runs")
    public List<ReportRun> runs(@PathVariable Long id,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
        return runs.findByReportIdOrderByStartedAtDesc(id, PageRequest.of(page, size)).getContent();
    }

    @GetMapping("/{id}/runs/{runId}/artifact")
    public ResponseEntity<FileSystemResource> artifact(@PathVariable Long id, @PathVariable Long runId) {
        ReportRun rr = runs.findById(runId).orElseThrow();
        if (!rr.getReportId().equals(id) || rr.getArtifactPath() == null) return ResponseEntity.notFound().build();
        File f = new File(rr.getArtifactPath());
        if (!f.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new FileSystemResource(f));
    }
}
