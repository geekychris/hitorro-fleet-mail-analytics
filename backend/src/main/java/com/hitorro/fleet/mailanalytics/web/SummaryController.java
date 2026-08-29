/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.summary.SummaryService;
import com.hitorro.fleet.mailanalytics.summary.SummaryStyle;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final SummaryService svc;

    public SummaryController(SummaryService svc) { this.svc = svc; }

    /** Enumerate available summarization styles for the UI dropdown. */
    @GetMapping("/styles")
    public List<Map<String, String>> styles() { return svc.listStyles(); }

    /** Summarize a thread — identified by its cluster key (normalized subject). */
    @PostMapping("/thread")
    public ResponseEntity<Map<String, Object>> thread(@RequestParam String key,
                                                      @RequestParam(defaultValue = "BRIEF") String style,
                                                      @RequestParam(required = false) Instant from,
                                                      @RequestParam(required = false) Instant to,
                                                      @RequestParam(required = false) String model) {
        SummaryStyle s = parseStyle(style);
        SummaryService.Result r = svc.summarizeThread(key, s, from, to, model);
        return r.ok() ? ResponseEntity.ok(r.asResponse()) : ResponseEntity.status(404).body(r.asResponse());
    }

    /** Summarize messages mentioning a named entity (e.g. from the Topics page). */
    @PostMapping("/entity")
    public ResponseEntity<Map<String, Object>> entity(@RequestParam String value,
                                                      @RequestParam(required = false) String kind,
                                                      @RequestParam(defaultValue = "BRIEF") String style,
                                                      @RequestParam(required = false) Instant from,
                                                      @RequestParam(required = false) Instant to,
                                                      @RequestParam(required = false) String model) {
        SummaryStyle s = parseStyle(style);
        SummaryService.Result r = svc.summarizeEntity(value, kind, from, to, s, model);
        return r.ok() ? ResponseEntity.ok(r.asResponse()) : ResponseEntity.status(404).body(r.asResponse());
    }

    private static SummaryStyle parseStyle(String raw) {
        try { return SummaryStyle.valueOf(raw.toUpperCase()); }
        catch (Exception e) { return SummaryStyle.BRIEF; }
    }
}
