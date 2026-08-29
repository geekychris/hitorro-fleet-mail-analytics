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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DomainService {

    private final RetrievalClient client;
    private final MailAnalyticsProperties props;

    public DomainService(RetrievalClient client, MailAnalyticsProperties props) {
        this.client = client;
        this.props = props;
    }

    private String index() { return props.getRetrieval().getDefaultIndex(); }

    public Map<String, Object> profile(String domain, Instant from, Instant to) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("domain", domain);
        out.put("total", count(domain, from, to));
        out.put("senders", topSenders(domain, from, to, 25));
        out.put("recent", messages(domain, from, to, 0, 20));
        return out;
    }

    public JsonNode messages(String domain, Instant from, Instant to, int offset, int limit) {
        QueryBuilder qb = QueryBuilder.over(index())
                .term("sender_domain", domain)
                .dateBetween("date_received.date_s",from, to)
                .sort("date_received:desc")
                .page(offset, limit);
        return client.execute(qb.buildExecute());
    }

    public List<MailQueryShapers.TopN> topSenders(String domain, Instant from, Instant to, int limit) {
        QueryBuilder qb = QueryBuilder.over(index())
                .term("sender_domain", domain)
                .dateBetween("date_received.date_s",from, to)
                .facet("sender_address")
                .page(0, 0);
        JsonNode resp = client.execute(qb.buildExecute());
        List<MailQueryShapers.TopN> out = new ArrayList<>();
        if (resp == null) return out;
        JsonNode vals = resp.path("facets").path("sender_address").path("values");
        for (JsonNode v : vals) {
            out.add(new MailQueryShapers.TopN(v.path("value").asText(), v.path("count").asLong()));
            if (out.size() >= limit) break;
        }
        return out;
    }

    private long count(String domain, Instant from, Instant to) {
        QueryBuilder qb = QueryBuilder.over(index())
                .term("sender_domain", domain)
                .dateBetween("date_received.date_s",from, to)
                .page(0, 0);
        JsonNode resp = client.execute(qb.buildExecute());
        return resp == null ? 0 : resp.path("totalHits").asLong();
    }
}
