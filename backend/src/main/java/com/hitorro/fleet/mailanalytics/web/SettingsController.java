/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final MailAnalyticsProperties props;

    public SettingsController(MailAnalyticsProperties props) { this.props = props; }

    /** Read-only view of the current effective config. Secrets are elided. */
    @GetMapping
    public Map<String, Object> get() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mode", props.getMode());
        out.put("retrieval", Map.of(
                "baseUrl", props.getRetrieval().getBaseUrl(),
                "timeoutMs", props.getRetrieval().getTimeoutMs(),
                "defaultIndex", props.getRetrieval().getDefaultIndex()));
        out.put("pipelines", Map.of(
                "home", props.getPipelines().getHome(),
                "hotDir", props.getPipelines().getHotDir(),
                "ingestJobYaml", props.getPipelines().getIngestJobYaml()));
        out.put("ingest", Map.of(
                "defaultCron", props.getIngest().getDefaultCron(),
                "batchSize", props.getIngest().getBatchSize(),
                "backfillHorizon", props.getIngest().getBackfillHorizon().toString(),
                "sources", props.getIngest().getSources().stream()
                        .map(s -> Map.of("id", s.getId(), "kind", s.getKind(),
                                         "enabled", s.isEnabled(),
                                         "cron", s.getCron() == null ? "" : s.getCron()))
                        .toList()));
        out.put("delivery", Map.of(
                "email", Map.of(
                        "enabled", props.getDelivery().getEmail().isEnabled(),
                        "host", props.getDelivery().getEmail().getHost(),
                        "port", props.getDelivery().getEmail().getPort(),
                        "from", props.getDelivery().getEmail().getFrom(),
                        "starttls", props.getDelivery().getEmail().isStarttls()),
                "webhook", Map.of(
                        "timeoutMs", props.getDelivery().getWebhook().getTimeoutMs(),
                        "maxRetries", props.getDelivery().getWebhook().getMaxRetries())));
        return out;
    }
}
