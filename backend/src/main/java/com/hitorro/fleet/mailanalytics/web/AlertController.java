/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.alerts.AlertEngine;
import com.hitorro.fleet.mailanalytics.alerts.AlertScheduler;
import com.hitorro.fleet.mailanalytics.entities.AlertFiring;
import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import com.hitorro.fleet.mailanalytics.repo.AlertFiringRepository;
import com.hitorro.fleet.mailanalytics.repo.AlertRuleRepository;
import org.springframework.data.domain.PageRequest;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertRuleRepository rules;
    private final AlertFiringRepository firings;
    private final AlertEngine engine;
    private final AlertScheduler scheduler;

    public AlertController(AlertRuleRepository rules, AlertFiringRepository firings,
                           AlertEngine engine, AlertScheduler scheduler) {
        this.rules = rules;
        this.firings = firings;
        this.engine = engine;
        this.scheduler = scheduler;
    }

    @GetMapping public List<AlertRule> list() { return rules.findAll(); }
    @GetMapping("/{id}") public AlertRule get(@PathVariable Long id) { return rules.findById(id).orElseThrow(); }

    @PostMapping public AlertRule create(@RequestBody AlertRule r) {
        r.setId(null);
        AlertRule saved = rules.save(r);
        scheduler.refresh();
        return saved;
    }

    @PutMapping("/{id}") public AlertRule update(@PathVariable Long id, @RequestBody AlertRule r) {
        r.setId(id);
        AlertRule saved = rules.save(r);
        scheduler.refresh();
        return saved;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        rules.deleteById(id);
        scheduler.refresh();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/run-now")
    public AlertEngine.Result runNow(@PathVariable Long id) {
        return engine.evaluate(id);
    }

    @PostMapping("/{id}/mute")
    public AlertRule mute(@PathVariable Long id, @RequestParam(defaultValue = "60") long minutes) {
        AlertRule r = rules.findById(id).orElseThrow();
        r.setMutedUntil(Instant.now().plus(Duration.ofMinutes(minutes)));
        return rules.save(r);
    }

    @PostMapping("/{id}/enable")
    public AlertRule enable(@PathVariable Long id, @RequestParam boolean enabled) {
        AlertRule r = rules.findById(id).orElseThrow();
        r.setEnabled(enabled);
        AlertRule saved = rules.save(r);
        scheduler.refresh();
        return saved;
    }

    @GetMapping("/{id}/firings")
    public List<AlertFiring> firings(@PathVariable Long id,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return firings.findByAlertRuleIdOrderByFiredAtDesc(id, PageRequest.of(page, size)).getContent();
    }

    @GetMapping("/firings")
    public List<AlertFiring> allFirings(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return firings.findAllByOrderByFiredAtDesc(PageRequest.of(page, size)).getContent();
    }
}
