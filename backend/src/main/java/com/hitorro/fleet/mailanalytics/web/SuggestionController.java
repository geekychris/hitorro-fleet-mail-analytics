/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.entities.EnrichmentSuggestion;
import com.hitorro.fleet.mailanalytics.entities.SuggestionStatus;
import com.hitorro.fleet.mailanalytics.repo.EnrichmentSuggestionRepository;
import com.hitorro.fleet.mailanalytics.suggestions.EnrichmentSuggester;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {

    private final EnrichmentSuggestionRepository repo;
    private final EnrichmentSuggester engine;

    public SuggestionController(EnrichmentSuggestionRepository repo, EnrichmentSuggester engine) {
        this.repo = repo;
        this.engine = engine;
    }

    @GetMapping
    public List<EnrichmentSuggestion> list() {
        return repo.findByStatusOrderByPriorityDescCreatedAtDesc(SuggestionStatus.NEW);
    }

    @PostMapping("/run-now")
    public Map<String, Integer> runNow() { return Map.of("emitted", engine.runNow()); }

    @PostMapping("/{id}/dismiss")
    public EnrichmentSuggestion dismiss(@PathVariable Long id) {
        EnrichmentSuggestion s = repo.findById(id).orElseThrow();
        s.setStatus(SuggestionStatus.DISMISSED);
        return repo.save(s);
    }

    @PostMapping("/{id}/reviewed")
    public EnrichmentSuggestion reviewed(@PathVariable Long id) {
        EnrichmentSuggestion s = repo.findById(id).orElseThrow();
        s.setStatus(SuggestionStatus.REVIEWED);
        return repo.save(s);
    }

    @PostMapping("/{id}/implemented")
    public ResponseEntity<EnrichmentSuggestion> implemented(@PathVariable Long id) {
        EnrichmentSuggestion s = repo.findById(id).orElseThrow();
        s.setStatus(SuggestionStatus.IMPLEMENTED);
        return ResponseEntity.ok(repo.save(s));
    }
}
