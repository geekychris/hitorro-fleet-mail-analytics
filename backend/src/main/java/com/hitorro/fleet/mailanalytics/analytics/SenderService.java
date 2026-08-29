/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.query.MailQueryShapers;
import com.hitorro.fleet.mailanalytics.query.QueryBuilder;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SenderService {

    private final RetrievalClient client;
    private final MailQueryShapers shapers;
    private final MailAnalyticsProperties props;

    public SenderService(RetrievalClient client, MailQueryShapers shapers, MailAnalyticsProperties props) {
        this.client = client;
        this.shapers = shapers;
        this.props = props;
    }

    private String index() { return props.getRetrieval().getDefaultIndex(); }

    public Map<String, Object> profile(String email, Instant from, Instant to) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("email", email);
        String domain = email.contains("@") ? email.substring(email.lastIndexOf('@') + 1) : "";
        out.put("domain", domain);
        out.put("total", count(email, from, to));
        out.put("unread", count(email, from, to, "read:false"));
        out.put("flagged", count(email, from, to, "flagged:true"));
        out.put("recent", messages(email, from, to, 0, 20));
        out.put("histogram", shapers.histogram("day", from, to,
                "sender_address:\"" + email.replace("\"", "\\\"") + "\""));
        return out;
    }

    public JsonNode messages(String email, Instant from, Instant to, int offset, int limit) {
        QueryBuilder qb = QueryBuilder.over(index())
                .term("sender_address", email)
                .dateBetween("date_received.date_s", from, to)
                .sort("date_received.date_s:desc")
                .page(offset, limit);
        return client.execute(qb.buildExecute());
    }

    private long count(String email, Instant from, Instant to, String... extra) {
        QueryBuilder qb = QueryBuilder.over(index())
                .term("sender_address", email)
                .dateBetween("date_received.date_s", from, to)
                .page(0, 0);
        for (String x : extra) qb.raw(x);
        JsonNode resp = client.execute(qb.buildExecute());
        return resp == null ? 0 : resp.path("totalHits").asLong();
    }
}
