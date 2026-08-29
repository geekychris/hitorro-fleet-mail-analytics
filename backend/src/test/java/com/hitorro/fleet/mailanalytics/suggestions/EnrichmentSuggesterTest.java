/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.suggestions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.entities.EnrichmentSuggestion;
import com.hitorro.fleet.mailanalytics.entities.QueryAudit;
import com.hitorro.fleet.mailanalytics.entities.SuggestionKind;
import com.hitorro.fleet.mailanalytics.entities.SuggestionStatus;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import com.hitorro.fleet.mailanalytics.repo.EnrichmentSuggestionRepository;
import com.hitorro.fleet.mailanalytics.repo.QueryAuditRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnrichmentSuggesterTest {

    @Test
    void unindexed_filter_field_produces_a_suggestion_after_10_hits() {
        Fixture f = new Fixture();
        for (int i = 0; i < 12; i++) f.recordQuery("/api/search/mail?priority=high");
        int emitted = f.subject().runNow();
        assertThat(emitted).isGreaterThanOrEqualTo(1);
        assertThat(f.stored)
                .anyMatch(s -> s.getKind() == SuggestionKind.UNINDEXED_FILTER
                            && "priority".equals(s.getTargetField()));
    }

    @Test
    void high_freq_ner_query_suggests_promoting_the_tag() {
        Fixture f = new Fixture();
        for (int i = 0; i < 6; i++)
            f.recordQuery("/api/search/mail?q=body.mls.segmented_ner:NE_Person");
        int emitted = f.subject().runNow();
        assertThat(emitted).isGreaterThanOrEqualTo(1);
        assertThat(f.stored)
                .anyMatch(s -> s.getKind() == SuggestionKind.HIGH_FREQ_NER
                            && "NE_Person".equals(s.getTargetField()));
    }

    @Test
    void content_scan_url_detector_fires_at_30pct_hit_rate() throws Exception {
        Fixture f = new Fixture();
        ObjectMapper m = new ObjectMapper();
        List<Map<String, Object>> docs = new ArrayList<>();
        for (int i = 0; i < 5; i++) docs.add(Map.of("body", Map.of("mls", List.of(Map.of("text", "hello world visit https://example.com/x")))));
        for (int i = 0; i < 5; i++) docs.add(Map.of("body", Map.of("mls", List.of(Map.of("text", "no links here at all")))));
        // doReturn form — plain when(...) would re-invoke the throwing stub set up in Fixture.
        org.mockito.Mockito.doReturn(m.readTree(m.writeValueAsString(Map.of("documents", docs))))
                .when(f.retrieval).execute(any());

        int emitted = f.subject().runNow();
        assertThat(f.stored).anyMatch(s -> s.getKind() == SuggestionKind.MISSING_URL_EXTRACT);
        assertThat(emitted).isGreaterThanOrEqualTo(1);
    }

    @Test
    void deduplicates_open_suggestions() {
        Fixture f = new Fixture();
        for (int i = 0; i < 12; i++) f.recordQuery("/api/search/mail?priority=high");
        f.subject().runNow();
        int first = f.stored.size();
        f.subject().runNow();
        assertThat(f.stored.size()).isEqualTo(first);   // no new duplicates
    }

    // ---- test harness ----

    static class Fixture {
        final QueryAuditRepository audits = mock(QueryAuditRepository.class);
        final EnrichmentSuggestionRepository suggestions = mock(EnrichmentSuggestionRepository.class);
        final RetrievalClient retrieval = mock(RetrievalClient.class);
        final MailAnalyticsProperties props = new MailAnalyticsProperties();
        final List<QueryAudit> auditRows = new ArrayList<>();
        final List<EnrichmentSuggestion> stored = new ArrayList<>();
        final Map<String, EnrichmentSuggestion> byKey = new HashMap<>();
        final ObjectMapper mapper = new ObjectMapper();
        final AtomicLong ids = new AtomicLong();

        Fixture() {
            when(audits.findByAtAfterOrderByAtDesc(any())).thenReturn(auditRows);
            when(suggestions.findFirstByKindAndTargetFieldAndStatus(any(), any(), any()))
                    .thenAnswer(inv -> {
                        String k = inv.getArgument(0) + "|" + inv.getArgument(1) + "|" + inv.getArgument(2);
                        return Optional.ofNullable(byKey.get(k));
                    });
            when(suggestions.save(any(EnrichmentSuggestion.class))).thenAnswer(inv -> {
                EnrichmentSuggestion s = inv.getArgument(0);
                if (s.getId() == null) s.setId(ids.incrementAndGet());
                stored.add(s);
                byKey.put(s.getKind() + "|" + s.getTargetField() + "|" + SuggestionStatus.NEW, s);
                return s;
            });
                // Default: retrieval unavailable. Tests can override via
            // doReturn(...).when(retrieval).execute(any()) — plain when(...)
            // would re-invoke this throwing stub during argument evaluation.
            org.mockito.Mockito.doThrow(new RuntimeException("retrieval unavailable"))
                    .when(retrieval).execute(any());
        }

        void recordQuery(String pathAndQuery) {
            int q = pathAndQuery.indexOf('?');
            String path = q < 0 ? pathAndQuery : pathAndQuery.substring(0, q);
            String query = q < 0 ? "" : pathAndQuery.substring(q + 1);
            Map<String, String> params = new HashMap<>();
            for (String pair : query.split("&")) {
                if (pair.isEmpty()) continue;
                String[] kv = pair.split("=", 2);
                params.put(kv[0], kv.length > 1 ? kv[1] : "");
            }
            QueryAudit a = new QueryAudit();
            a.setPath(path);
            try { a.setQueryJson(mapper.writeValueAsString(params)); } catch (Exception e) { throw new RuntimeException(e); }
            a.setAt(Instant.now());
            auditRows.add(a);
        }

        EnrichmentSuggester subject() {
            return new EnrichmentSuggester(audits, suggestions, retrieval, props);
        }
    }
}
