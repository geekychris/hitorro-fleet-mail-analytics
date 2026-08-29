/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.analytics;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TrendService {

    private final DashboardService dashboard;

    public TrendService(DashboardService dashboard) { this.dashboard = dashboard; }

    /** Window = 7d, 30d, 90d. Compares current window to prior window of same size. */
    public Map<String, Object> compute(String window) {
        Duration d = switch (window) {
            case "30d" -> Duration.ofDays(30);
            case "90d" -> Duration.ofDays(90);
            default -> Duration.ofDays(7);
        };
        Instant now = Instant.now();
        Instant currentFrom = now.minus(d);
        Instant priorFrom = currentFrom.minus(d);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("window", window);
        out.put("current", tally(currentFrom, now));
        out.put("prior", tally(priorFrom, currentFrom));
        return out;
    }

    private Map<String, Object> tally(Instant from, Instant to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("from", from);
        m.put("to", to);
        m.put("total", dashboard.count(from, to, null));
        m.put("unread", dashboard.count(from, to, "read:false"));
        m.put("flagged", dashboard.count(from, to, "flagged:true"));
        m.put("newsletters", dashboard.count(from, to, "is_newsletter:true"));
        return m;
    }
}
