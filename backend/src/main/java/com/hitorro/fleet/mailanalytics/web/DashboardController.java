/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.analytics.DashboardService;
import com.hitorro.fleet.mailanalytics.analytics.TrendService;
import com.hitorro.fleet.mailanalytics.query.MailQueryShapers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;
    private final MailQueryShapers shapers;
    private final TrendService trends;

    public DashboardController(DashboardService dashboard, MailQueryShapers shapers, TrendService trends) {
        this.dashboard = dashboard;
        this.shapers = shapers;
        this.trends = trends;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(required = false) Instant from,
                                        @RequestParam(required = false) Instant to) {
        return dashboard.overview(from, to);
    }

    @GetMapping("/histogram")
    public List<Map<String, Object>> histogram(@RequestParam(defaultValue = "day") String bucket,
                                               @RequestParam(required = false) Instant from,
                                               @RequestParam(required = false) Instant to,
                                               @RequestParam(required = false) String filter) {
        return shapers.histogram(bucket, from, to, filter).stream()
                .map(MailQueryShapers.Bucket::asJson).toList();
    }

    @GetMapping("/top-senders")
    public List<MailQueryShapers.TopN> topSenders(@RequestParam(required = false) Instant from,
                                                  @RequestParam(required = false) Instant to,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return shapers.topSenders(from, to, limit);
    }

    @GetMapping("/top-domains")
    public List<MailQueryShapers.TopN> topDomains(@RequestParam(required = false) Instant from,
                                                  @RequestParam(required = false) Instant to,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return shapers.topDomains(from, to, limit);
    }

    @GetMapping("/top-entities")
    public List<MailQueryShapers.TopN> topEntities(@RequestParam(defaultValue = "PERSON") String kind,
                                                   @RequestParam(required = false) Instant from,
                                                   @RequestParam(required = false) Instant to,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return shapers.topEntities(kind, from, to, limit);
    }

    @GetMapping("/action-candidates")
    public JsonNode actionCandidates(@RequestParam(defaultValue = "20") int limit) {
        return shapers.actionCandidates(limit);
    }

    @GetMapping("/trends")
    public Map<String, Object> trends(@RequestParam(defaultValue = "7d") String window) {
        return trends.compute(window);
    }
}
