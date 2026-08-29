/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight health / info surface. Actuator provides {@code /actuator/health}
 * and {@code /actuator/info} — this adds a mail-analytics-specific status blob
 * (mode, retrieval target, configured sources) for the UI's Settings page.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final MailAnalyticsProperties props;

    public HealthController(MailAnalyticsProperties props) {
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "mode", props.getMode(),
                "retrieval", Map.of(
                        "baseUrl", props.getRetrieval().getBaseUrl(),
                        "defaultIndex", props.getRetrieval().getDefaultIndex()),
                "sources", props.getIngest().getSources().stream()
                        .map(s -> Map.of("id", s.getId(), "kind", s.getKind(),
                                         "enabled", s.isEnabled()))
                        .toList()
        );
    }
}
