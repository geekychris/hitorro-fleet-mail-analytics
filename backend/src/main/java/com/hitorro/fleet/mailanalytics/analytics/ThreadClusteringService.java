/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.query.QueryBuilder;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cheap subject-prefix clustering. Groups messages whose normalized
 *  subject (Re:/Fwd:/[list] stripped) matches. Enough to power a
 *  "Threads" view without needing Message-ID / References parsing. */
@Service
public class ThreadClusteringService {

    private final RetrievalClient client;
    private final MailAnalyticsProperties props;

    public ThreadClusteringService(RetrievalClient client, MailAnalyticsProperties props) {
        this.client = client;
        this.props = props;
    }

    public List<Cluster> clusters(Instant from, Instant to, int scanLimit) {
        QueryBuilder qb = QueryBuilder.over(props.getRetrieval().getDefaultIndex())
                .dateBetween("date_received.date_s", from, to)
                .sort("date_received.date_s:desc")
                .page(0, Math.min(500, scanLimit));
        JsonNode resp = client.execute(qb.buildExecute());
        if (resp == null || !resp.path("documents").isArray()) return List.of();

        Map<String, Cluster> byKey = new HashMap<>();
        for (JsonNode doc : resp.path("documents")) {
            String subj = subjectOf(doc);
            String key = normalize(subj);
            if (key.isEmpty()) continue;
            byKey.computeIfAbsent(key, k -> new Cluster(k, subj))
                    .add(doc);
        }
        List<Cluster> out = new ArrayList<>(byKey.values());
        out.sort(Comparator.comparingInt(Cluster::messageCount).reversed());
        return out;
    }

    private static String subjectOf(JsonNode doc) {
        JsonNode t = doc.path("title").path("mls");
        if (t.isArray() && t.size() > 0) return t.get(0).path("text").asText("");
        return "";
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String v = s.trim();
        while (true) {
            String lower = v.toLowerCase();
            if (lower.startsWith("re:")) v = v.substring(3).trim();
            else if (lower.startsWith("fwd:") || lower.startsWith("fw:")) v = v.substring(lower.startsWith("fwd:") ? 4 : 3).trim();
            else if (v.startsWith("[")) {
                int end = v.indexOf(']');
                if (end > 0) { v = v.substring(end + 1).trim(); continue; }
                break;
            } else break;
        }
        return v.toLowerCase();
    }

    public static class Cluster {
        private final String key;
        private final String subject;
        private final List<Map<String, Object>> messages = new ArrayList<>();
        public Cluster(String key, String subject) { this.key = key; this.subject = subject; }
        // JavaBean-style getters so Jackson serializes them by default.
        public String getKey() { return key; }
        public String getSubject() { return subject; }
        public int getMessageCount() { return messages.size(); }
        public List<Map<String, Object>> getMessages() { return messages; }
        // Kept for the java-only callers that use the record-style accessors.
        public int messageCount() { return messages.size(); }
        public String key() { return key; }
        void add(JsonNode doc) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", doc.path("id"));
            m.put("subject", doc.path("title").path("mls").path(0).path("text").asText(""));
            m.put("sender", doc.path("sender_address").asText(""));
            m.put("date", doc.path("times").path("date_received").asLong(0));
            messages.add(m);
        }
    }
}
