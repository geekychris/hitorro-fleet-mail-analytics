/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.analytics;

import com.hitorro.fleet.mailanalytics.query.MailQueryShapers;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wraps entity + noun rollups for the Topics page. */
@Service
public class TopicService {

    private final MailQueryShapers shapers;

    public TopicService(MailQueryShapers shapers) { this.shapers = shapers; }

    public Map<String, List<MailQueryShapers.TopN>> entityRollup(Instant from, Instant to, int limit) {
        Map<String, List<MailQueryShapers.TopN>> out = new LinkedHashMap<>();
        out.put("persons", shapers.topEntities("PERSON", from, to, limit));
        out.put("organizations", shapers.topEntities("ORG", from, to, limit));
        out.put("locations", shapers.topEntities("LOC", from, to, limit));
        out.put("dates", shapers.topEntities("DATE", from, to, limit));
        return out;
    }
}
