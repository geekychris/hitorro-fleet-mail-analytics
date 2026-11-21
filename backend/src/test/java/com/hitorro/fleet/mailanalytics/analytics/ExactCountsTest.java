/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.analytics;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.hitorro.fleet.mailanalytics.config.JsonValues;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.query.HitCounts;
import com.hitorro.fleet.mailanalytics.query.MailQueryShapers;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Counts above 1,000 against a stub fleet-retrieval that behaves like Lucene:
 * 2,476 synthetic documents, {@code totalHits} capped as a lower bound (1001)
 * past 1,000 matches, facet counts exact.
 */
class ExactCountsTest {

    static final JsonNodeFactory NF = JsonNodeFactory.instance;
    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.HOURS);

    record Doc(boolean read, boolean flagged, String domain, long millis) {}

    final List<Doc> docs = new ArrayList<>();
    RetrievalClient client;
    MailAnalyticsProperties props;

    @BeforeEach
    void stub() {
        // 2,476 documents over the last 6 days: 666 read, 12 flagged, 3 domains.
        for (int i = 0; i < 2476; i++) {
            docs.add(new Doc(i < 666, i % 200 == 0, "d" + (i % 3) + ".example",
                    NOW.minus(i % 6, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS).toEpochMilli()));
        }
        client = mock(RetrievalClient.class);
        when(client.execute(any())).thenAnswer(inv -> answer(inv.getArgument(0)));
        props = new MailAnalyticsProperties();
    }

    JsonNode answer(JsonNode body) {
        JsonNode search = body.path("query").path("search");
        String q = JsonValues.asText(search.path("query"), "*:*");
        Predicate<Doc> p = d -> true;
        for (String part : q.split(" AND ")) {
            part = part.trim();
            if (part.equals("read:false")) p = p.and(d -> !d.read());
            else if (part.equals("read:true")) p = p.and(Doc::read);
            else if (part.equals("flagged:true")) p = p.and(Doc::flagged);
            else if (part.startsWith("date_received.date_s:[")) {
                String[] r = part.substring(part.indexOf('[') + 1, part.indexOf(']')).split(" TO ");
                long lo = r[0].equals("*") ? Long.MIN_VALUE : Long.parseLong(r[0]);
                long hi = r[1].equals("*") ? Long.MAX_VALUE : Long.parseLong(r[1]);
                p = p.and(d -> d.millis() >= lo && d.millis() <= hi);
            }
        }
        List<Doc> hits = docs.stream().filter(p).toList();
        ObjectNode resp = NF.objectNode();
        // Lucene's default totalHitsThreshold: an exact count only up to 1,000.
        resp.put("totalHits", hits.size() > 1000 ? 1001 : hits.size());
        resp.putArray("documents");
        ObjectNode facets = resp.putObject("facets");
        for (JsonNode f : search.path("facets")) {
            String dim = JsonValues.asText(f);
            Map<String, Long> counts = new LinkedHashMap<>();
            for (Doc d : hits) {
                String v = switch (dim) {
                    case "read" -> String.valueOf(d.read());
                    case "flagged" -> String.valueOf(d.flagged());
                    case "sender_domain" -> d.domain();
                    default -> null;
                };
                if (v != null) counts.merge(v, 1L, Long::sum);
            }
            var vals = facets.putObject(dim).putArray("values");
            counts.forEach((k, c) -> vals.addObject().put("value", k).put("count", c));
        }
        return resp;
    }

    @Test
    void overview_counts_are_exact_above_one_thousand() {
        DashboardService svc = new DashboardService(new MailQueryShapers(client, props), client, props);
        Map<String, Object> o = svc.overview(NOW.minus(30, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.HOURS));
        assertThat(o.get("total")).isEqualTo(2476L);
        assertThat(o.get("unread")).isEqualTo(1810L);
        assertThat(o.get("flagged")).isEqualTo(13L);
    }

    @Test
    void histogram_buckets_are_exact() {
        MailQueryShapers shapers = new MailQueryShapers(client, props);
        var buckets = shapers.histogram("week", NOW.minus(7, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.HOURS), null);
        long sum = buckets.stream().mapToLong(MailQueryShapers.Bucket::count).sum();
        assertThat(sum).isEqualTo(2476L);
        assertThat(buckets.stream().mapToLong(MailQueryShapers.Bucket::count).max().orElse(0)).isGreaterThan(1000L);
    }

    @Test
    void falls_back_to_total_hits_without_the_count_facet() {
        ObjectNode r = NF.objectNode();
        r.put("totalHits", 42);
        assertThat(HitCounts.exact(r)).isEqualTo(42L);
        assertThat(HitCounts.exact(null)).isZero();
    }
}
