/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.query.MailQueryShapers;
import com.hitorro.fleet.mailanalytics.query.QueryBuilder;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DashboardService {

    private final MailQueryShapers shapers;
    private final RetrievalClient client;
    private final MailAnalyticsProperties props;

    public DashboardService(MailQueryShapers shapers, RetrievalClient client, MailAnalyticsProperties props) {
        this.shapers = shapers;
        this.client = client;
        this.props = props;
    }

    public Map<String, Object> overview(Instant from, Instant to) {
        if (to == null) to = Instant.now();
        if (from == null) from = to.minus(30, ChronoUnit.DAYS);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowFrom", from);
        out.put("windowTo", to);
        out.put("total", count(from, to, null));
        out.put("unread", count(from, to, "read:false"));
        out.put("flagged", count(from, to, "flagged:true"));
        out.put("newsletters", count(from, to, "is_newsletter:true"));
        out.put("topSenders", shapers.topSenders(from, to, 10));
        out.put("topDomains", shapers.topDomains(from, to, 10));
        out.put("topPersons", shapers.topEntities("PERSON", from, to, 10));
        out.put("topOrgs", shapers.topEntities("ORG", from, to, 10));
        out.put("actionCandidates", shapers.actionCandidates(5));
        return out;
    }

    public long count(Instant from, Instant to, String extraFilter) {
        QueryBuilder qb = QueryBuilder.over(props.getRetrieval().getDefaultIndex())
                .dateBetween("date_received.date_s", from, to)
                .raw(extraFilter)
                .page(0, 0);
        JsonNode resp = client.execute(qb.buildExecute());
        return resp == null ? 0 : resp.path("totalHits").asLong();
    }
}
