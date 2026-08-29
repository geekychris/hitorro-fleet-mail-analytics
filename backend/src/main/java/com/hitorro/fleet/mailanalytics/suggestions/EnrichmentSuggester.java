/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.suggestions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.entities.EnrichmentSuggestion;
import com.hitorro.fleet.mailanalytics.entities.QueryAudit;
import com.hitorro.fleet.mailanalytics.entities.SuggestionKind;
import com.hitorro.fleet.mailanalytics.entities.SuggestionStatus;
import com.hitorro.fleet.mailanalytics.query.QueryBuilder;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import com.hitorro.fleet.mailanalytics.repo.EnrichmentSuggestionRepository;
import com.hitorro.fleet.mailanalytics.repo.QueryAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Periodic scan → proposes new pipeline enrichments. Two detector families:
 * <ul>
 *   <li><b>QueryAudit-only</b> — cheap; runs every scheduled tick.
 *     UNINDEXED_FILTER (filter fields not on the type) and HIGH_FREQ_NER
 *     (repeated NER-typed queries → propose a first-class field).</li>
 *   <li><b>Content-sampling</b> — samples recent bodies from fleet-retrieval;
 *     skipped if retrieval is unreachable. MISSING_URL_EXTRACT /
 *     MISSING_PHONE_EXTRACT / MISSING_TRACKING_EXTRACT (heuristic
 *     regex hit rate ≥ 30% → suggest a dedicated enricher).</li>
 * </ul>
 */
@Service
public class EnrichmentSuggester {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentSuggester.class);

    // Content-sampling detectors need a body corpus. Use these patterns to
    // score hit-rate; cheap heuristics, not full extractors.
    private static final Pattern URL_RE = Pattern.compile("https?://\\S+");
    private static final Pattern PHONE_RE = Pattern.compile(
            "(?:\\+?\\d{1,3}[\\s-]?)?\\(?\\d{3}\\)?[\\s.-]\\d{3}[\\s.-]\\d{4}");
    private static final Pattern TRACKING_RE = Pattern.compile(
            "\\b(?:1Z[0-9A-Z]{16}|\\d{12,22})\\b");

    private static final double CONTENT_HIT_THRESHOLD = 0.30;
    private static final int NER_QUERY_MIN = 5;

    private final QueryAuditRepository audits;
    private final EnrichmentSuggestionRepository suggestions;
    private final RetrievalClient retrieval;
    private final MailAnalyticsProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnrichmentSuggester(QueryAuditRepository audits,
                               EnrichmentSuggestionRepository suggestions,
                               RetrievalClient retrieval,
                               MailAnalyticsProperties props) {
        this.audits = audits;
        this.suggestions = suggestions;
        this.retrieval = retrieval;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT2M")
    public void run() { runNow(); }

    @Transactional
    public int runNow() {
        // Purge audit rows older than 24h first — bounded state so
        // the audit table can't balloon the H2 file / JVM heap.
        long purged = audits.deleteByAtBefore(Instant.now().minus(24, ChronoUnit.HOURS));
        if (purged > 0) log.info("EnrichmentSuggester purged {} old audit rows", purged);
        int emitted = 0;
        emitted += scanAudits();
        emitted += scanContent();
        if (emitted > 0) log.info("EnrichmentSuggester emitted {} new suggestions", emitted);
        return emitted;
    }

    // ---- QueryAudit-driven detectors ----------------------------------

    private int scanAudits() {
        int emitted = 0;
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        List<QueryAudit> recent = audits.findByAtAfterOrderByAtDesc(since);
        Map<String, Integer> filterHits = new HashMap<>();
        Map<String, Integer> nerHits = new HashMap<>();

        for (QueryAudit a : recent) {
            try {
                JsonNode params = mapper.readTree(a.getQueryJson() == null ? "{}" : a.getQueryJson());
                params.fields().forEachRemaining(e -> {
                    if (IGNORE_PARAMS.contains(e.getKey())) return;
                    filterHits.merge(e.getKey(), 1, Integer::sum);
                });
                String q = params.path("q").asText("");
                if (q.contains("NE_")) {
                    // Free-text query targeting an NER-tagged bracket
                    for (String tag : NER_TAGS) {
                        if (q.contains(tag)) nerHits.merge(tag, 1, Integer::sum);
                    }
                }
            } catch (Exception ignore) {}
        }

        for (var e : filterHits.entrySet()) {
            if (e.getValue() >= 10 && !INDEXED_FIELDS.contains(e.getKey())) {
                emitted += upsert(SuggestionKind.UNINDEXED_FILTER, e.getKey(),
                        "Consider indexing filter field '" + e.getKey() + "'",
                        "Used " + e.getValue() + " times in last 24h but not a first-class indexed field.",
                        Map.of("field", e.getKey(), "hits24h", e.getValue()));
            }
        }
        for (var e : nerHits.entrySet()) {
            if (e.getValue() >= NER_QUERY_MIN) {
                emitted += upsert(SuggestionKind.HIGH_FREQ_NER, e.getKey(),
                        "Promote '" + e.getKey() + "' to first-class field",
                        "Users searched for this NER tag " + e.getValue() +
                                " times in last 24h — a dedicated indexed field is faster than a body substring match.",
                        Map.of("nerTag", e.getKey(), "hits24h", e.getValue()));
            }
        }
        return emitted;
    }

    // ---- Content-sampling detectors -----------------------------------

    private int scanContent() {
        JsonNode sample;
        try {
            sample = retrieval.execute(QueryBuilder.over(props.getRetrieval().getDefaultIndex())
                    .page(0, 200)
                    .sort("date_received:desc")
                    .buildExecute());
        } catch (Exception e) {
            log.debug("suggester skipped content scan — retrieval unreachable: {}", e.getMessage());
            return 0;
        }
        if (sample == null || !sample.path("documents").isArray()) return 0;
        int n = 0, url = 0, phone = 0, tracking = 0;
        Map<String, Integer> listyDomains = new HashMap<>();
        for (JsonNode doc : sample.path("documents")) {
            String body = doc.path("body").path("mls").path(0).path("text").asText("");
            if (body.isEmpty()) continue;
            n++;
            if (URL_RE.matcher(body).find()) url++;
            if (PHONE_RE.matcher(body).find()) phone++;
            if (TRACKING_RE.matcher(body).find()) tracking++;
            if (!doc.path("is_newsletter").asBoolean(false)) {
                String domain = doc.path("sender_domain").asText("");
                // heuristic: newsletter-shaped body without newsletter flag
                if (!domain.isEmpty() && body.toLowerCase().contains("unsubscribe")) {
                    listyDomains.merge(domain, 1, Integer::sum);
                }
            }
        }
        if (n == 0) return 0;

        int emitted = 0;
        if ((double) url / n >= CONTENT_HIT_THRESHOLD) {
            emitted += upsert(SuggestionKind.MISSING_URL_EXTRACT, "body.urls",
                    "Add URL extractor to mail_email",
                    hitRateRationale("URLs", url, n),
                    Map.of("sampleSize", n, "hits", url, "pattern", URL_RE.pattern()));
        }
        if ((double) phone / n >= CONTENT_HIT_THRESHOLD) {
            emitted += upsert(SuggestionKind.MISSING_PHONE_EXTRACT, "body.phones",
                    "Add phone-number extractor to mail_email",
                    hitRateRationale("phone numbers", phone, n),
                    Map.of("sampleSize", n, "hits", phone, "pattern", PHONE_RE.pattern()));
        }
        if ((double) tracking / n >= CONTENT_HIT_THRESHOLD) {
            emitted += upsert(SuggestionKind.MISSING_TRACKING_EXTRACT, "body.tracking",
                    "Add tracking-number extractor to mail_email",
                    hitRateRationale("tracking numbers", tracking, n),
                    Map.of("sampleSize", n, "hits", tracking, "pattern", TRACKING_RE.pattern()));
        }
        for (var e : listyDomains.entrySet()) {
            if (e.getValue() >= 5) {
                emitted += upsert(SuggestionKind.MISSING_NEWSLETTER_FLAG, e.getKey(),
                        "Flag '" + e.getKey() + "' as newsletter source",
                        "Domain has " + e.getValue() + " unsubscribe-bearing messages in the last sample but is not marked is_newsletter.",
                        Map.of("domain", e.getKey(), "unsubscribeHits", e.getValue()));
            }
        }
        return emitted;
    }

    private static String hitRateRationale(String label, int hits, int n) {
        return String.format("%d of %d recent bodies (%.0f%%) contain %s. A dedicated enricher lets you facet + filter on them.",
                hits, n, 100.0 * hits / n, label);
    }

    private int upsert(SuggestionKind kind, String targetField, String title, String rationale, Map<String, Object> evidence) {
        var existing = suggestions.findFirstByKindAndTargetFieldAndStatus(kind, targetField, SuggestionStatus.NEW);
        if (existing.isPresent()) return 0;
        EnrichmentSuggestion s = new EnrichmentSuggestion();
        s.setKind(kind);
        s.setTargetField(targetField);
        s.setTitle(title);
        s.setRationale(rationale);
        Map<String, Object> ev = new LinkedHashMap<>(evidence);
        try { s.setEvidenceJson(mapper.writeValueAsString(ev)); } catch (Exception ignore) {}
        s.setPriority(60);
        suggestions.save(s);
        return 1;
    }

    /** Parameters that don't represent user-selected filter fields. */
    private static final java.util.Set<String> IGNORE_PARAMS = java.util.Set.of(
            "from", "to", "offset", "limit", "page", "size", "sort", "q", "bucket", "kind",
            "scanLimit", "window", "daysBack", "minutes", "enabled");

    /** Fields already indexed on mail_email (see config/types/mail_email.json). */
    private static final java.util.Set<String> INDEXED_FIELDS = java.util.Set.of(
            "sender_address", "sender_domain", "sender_name", "mailbox_url",
            "read", "flagged", "is_newsletter", "size_bytes", "recipient_count",
            "date_received");

    /** NER bracket types the jvs-enrich step emits into body.mls.segmented_ner. */
    private static final java.util.Set<String> NER_TAGS = java.util.Set.of(
            "NE_Person", "NE_Organization", "NE_Location", "NE_Date", "NE_Money");
}
