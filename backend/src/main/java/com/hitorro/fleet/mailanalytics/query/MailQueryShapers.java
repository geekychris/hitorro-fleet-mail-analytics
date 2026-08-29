/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Canned query shapes used by the dashboard, drilldown, and alert engine. */
@Component
public class MailQueryShapers {

    private final RetrievalClient client;
    private final MailAnalyticsProperties props;

    public MailQueryShapers(RetrievalClient client, MailAnalyticsProperties props) {
        this.client = client;
        this.props = props;
    }

    private String index() { return props.getRetrieval().getDefaultIndex(); }

    /** Run a caller-shaped query and return the raw response. */
    public JsonNode run(QueryBuilder q) { return client.execute(q.buildExecute()); }

    /** Free-text + optional date range → paged results with the standard
     *  facet dimensions attached. Bare user text (no {@code :} in the
     *  query) is expanded across the enriched text fields so a plain
     *  word like {@code meeting} matches both subject + body. Explicit
     *  Lucene syntax is passed through untouched. */
    public JsonNode search(String q, Instant from, Instant to, int offset, int limit, String sort) {
        QueryBuilder qb = QueryBuilder.over(index())
                .dateBetween("date_received.date_s", from, to)
                .facet("sender_domain")
                .facet("sender_address")
                .facet("is_newsletter")
                .facet("read")
                .facet("flagged")
                .page(offset, limit);
        if (q != null && !q.isBlank()) {
            String trimmed = q.trim();
            if (trimmed.contains(":") || trimmed.equals("*:*")) {
                // User (or a saved query) is speaking Lucene directly.
                qb.raw(trimmed);
            } else {
                // Bare term(s): quote if it looks like a phrase, fan out
                // across the default enriched text fields.
                String quoted = escapeForPhrase(trimmed);
                qb.raw("(body.mls.clean.text_en_m:" + quoted
                        + " OR title.mls.clean.text_en_m:" + quoted + ")");
            }
        }
        if (sort != null && !sort.isBlank()) qb.sort(sort);
        return run(qb);
    }

    /** Wrap a value in quotes when it contains a space or a Lucene
     *  reserved character. Escape embedded quotes + backslashes. */
    private static String escapeForPhrase(String v) {
        boolean needsQuote = v.chars().anyMatch(c ->
                Character.isWhitespace(c) || "+-!(){}[]^\"~*?:\\/".indexOf(c) >= 0);
        if (!needsQuote) return v;
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Top-N sender addresses in a time window. */
    public List<TopN> topSenders(Instant from, Instant to, int limit) {
        return topDimensionValues("sender_address", from, to, limit);
    }

    public List<TopN> topDomains(Instant from, Instant to, int limit) {
        return topDimensionValues("sender_domain", from, to, limit);
    }

    /** NER entities parsed out of body.mls.segmented_ner. Kind = PERSON|ORG|LOC|DATE.
     *  Fleet-retrieval only facets identifier-method fields (StringField); the
     *  NER field is text-analyzed, so faceting is impossible from that layer.
     *  Fallback: sample recent matching docs and parse the entity brackets
     *  in-process. Bounded scan (200 docs) — cheap enough for a dashboard tile. */
    public List<TopN> topEntities(String kind, Instant from, Instant to, int limit) {
        String neTag = neTagFor(kind);
        // No pre-filter on the analyzed textmarkup field — the English
        // stemmer chops NE tags inconsistently ("Person" stays whole,
        // "Organization" stems to "organiz"), so a `field:NE_Xxx` filter
        // silently drops the majority of matches. Scan a bounded window
        // of recent docs and parse the segmented_ner strings in Java.
        QueryBuilder qb = QueryBuilder.over(index())
                .dateBetween("date_received.date_s", from, to)
                .page(0, 500);
        JsonNode resp = run(qb);
        if (resp == null) return List.of();
        // Enrichment writes each sentence as e.g.
        //   "[{Alice&&NE_Person}] met [{Bob&&NE_Person}] in [{Paris&&NE_Location}]"
        // Extract the {token && matching NE_tag} pairs for this kind and count.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "\\[\\{([^}&]+?)&&" + java.util.regex.Pattern.quote(neTag) + "\\}\\]");
        Map<String, Long> counts = new java.util.HashMap<>();
        for (JsonNode doc : resp.path("documents")) {
            JsonNode sentences = doc.path("body").path("mls").path(0).path("segmented_ner");
            if (!sentences.isArray()) continue;
            for (JsonNode s : sentences) {
                java.util.regex.Matcher m = p.matcher(s.asText());
                while (m.find()) {
                    String tok = m.group(1).trim();
                    if (tok.length() < 2) continue;
                    counts.merge(tok, 1L, Long::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new TopN(e.getKey(), e.getValue()))
                .toList();
    }

    /** Time bucket counts. Bucket = hour|day|week. */
    public List<Bucket> histogram(String bucket, Instant from, Instant to, String extraFilter) {
        List<Bucket> out = new ArrayList<>();
        if (from == null) from = Instant.now().minus(30, ChronoUnit.DAYS);
        if (to == null) to = Instant.now();
        ChronoUnit unit = switch (bucket == null ? "day" : bucket) {
            case "hour" -> ChronoUnit.HOURS;
            case "week" -> ChronoUnit.WEEKS;
            default -> ChronoUnit.DAYS;
        };
        Instant cursor = from.truncatedTo(unit == ChronoUnit.WEEKS ? ChronoUnit.DAYS : unit);
        while (cursor.isBefore(to)) {
            Instant next = cursor.plus(1, unit);
            QueryBuilder qb = QueryBuilder.over(index())
                    .dateBetween("date_received.date_s",cursor, next)
                    .raw(extraFilter)
                    .page(0, 0);
            JsonNode resp = run(qb);
            long count = totalHits(resp);
            out.add(new Bucket(cursor, count));
            cursor = next;
        }
        return out;
    }

    /** Action candidates: unread & not newsletter & has a human sender. */
    public JsonNode actionCandidates(int limit) {
        QueryBuilder qb = QueryBuilder.over(index())
                .booleanFlag("read", false)
                .booleanFlag("is_newsletter", false)
                .sort("times.date_received:desc")
                .page(0, limit);
        return run(qb);
    }

    // ------------------------------------------------------------------- helpers

    private List<TopN> topDimensionValues(String field, Instant from, Instant to, int limit) {
        QueryBuilder qb = QueryBuilder.over(index())
                .dateBetween("date_received.date_s",from, to)
                .facet(field)
                .page(0, 0);
        return facetValuesTopN(run(qb), field, limit, v -> true);
    }

    private long totalHits(JsonNode resp) {
        if (resp == null) return 0;
        JsonNode n = resp.get("totalHits");
        return n == null ? 0 : n.asLong();
    }

    private List<TopN> facetValuesTopN(JsonNode resp, String dim, int limit,
                                       java.util.function.Predicate<String> filter) {
        List<TopN> out = new ArrayList<>();
        if (resp == null) return out;
        JsonNode facets = resp.path("facets");
        JsonNode dimNode = facets.path(dim);
        JsonNode values = dimNode.path("values");
        if (!values.isArray()) return out;
        for (JsonNode v : values) {
            String label = v.path("value").asText();
            if (!filter.test(label)) continue;
            out.add(new TopN(label, v.path("count").asLong()));
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    /** Map the short kind (used by dashboard tiles + topic controller) to
     *  the actual NE_ tag OpenNLP emits into segmented_ner. */
    private static String neTagFor(String kind) {
        if (kind == null) return "NE_Person";
        return switch (kind.toUpperCase()) {
            case "PERSON", "PER" -> "NE_Person";
            case "ORG", "ORGANIZATION" -> "NE_Organization";
            case "LOC", "LOCATION" -> "NE_Location";
            case "DATE" -> "NE_Date";
            case "MONEY" -> "NE_Money";
            default -> "NE_" + capitalize(kind);
        };
    }

    public record TopN(String value, long count) {}
    public record Bucket(Instant at, long count) {
        public Map<String, Object> asJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", at.toString());
            m.put("count", count);
            return m;
        }
    }
}
