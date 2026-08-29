/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.entities.SavedQuery;
import com.hitorro.fleet.mailanalytics.repo.SavedQueryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SavedQueryService {

    private final SavedQueryRepository repo;
    private final RetrievalClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public SavedQueryService(SavedQueryRepository repo, RetrievalClient client) {
        this.repo = repo;
        this.client = client;
    }

    public List<SavedQuery> list() { return repo.findAllByOrderByUpdatedAtDesc(); }
    public SavedQuery get(Long id) { return repo.findById(id).orElseThrow(); }
    public SavedQuery save(SavedQuery q) { return repo.save(q); }
    public void delete(Long id) { repo.deleteById(id); }

    /** Compile the DSL JSON into a QueryBuilder and run it. Supported keys:
     *  {@code index, text, filters:{field:value|[values]}, from, to, sort, facets:[], offset, limit}. */
    public JsonNode run(Long id) {
        SavedQuery sq = get(id);
        try {
            JsonNode dsl = mapper.readTree(sq.getDslJson());
            QueryBuilder qb = QueryBuilder.over(dsl.path("index").asText("mail"))
                    .text(dsl.path("text").asText(""))
                    .page(dsl.path("offset").asInt(0),
                          dsl.path("limit").asInt(20));
            String from = dsl.path("from").asText(null);
            String to = dsl.path("to").asText(null);
            Instant fromI = from == null || from.isEmpty() ? null : Instant.parse(from);
            Instant toI = to == null || to.isEmpty() ? null : Instant.parse(to);
            if (fromI != null || toI != null) qb.dateBetween("date_received.date_s", fromI, toI);
            JsonNode filters = dsl.path("filters");
            if (filters.isObject()) {
                filters.fields().forEachRemaining(e -> {
                    JsonNode v = e.getValue();
                    if (v.isArray()) {
                        java.util.List<String> vals = new java.util.ArrayList<>();
                        v.forEach(x -> vals.add(x.asText()));
                        qb.termIn(e.getKey(), vals);
                    } else {
                        qb.term(e.getKey(), v.asText());
                    }
                });
            }
            JsonNode facets = dsl.path("facets");
            if (facets.isArray()) facets.forEach(f -> qb.facet(f.asText()));
            String sort = dsl.path("sort").asText(null);
            if (sort != null && !sort.isEmpty()) qb.sort(sort);
            return client.execute(qb.buildExecute());
        } catch (Exception e) {
            throw new RuntimeException("saved query " + id + " DSL invalid: " + e.getMessage(), e);
        }
    }

    /** Recent runs freshness: {@code updated_at} within {@code minutes}. */
    public boolean recentlyRun(SavedQuery q, long minutes) {
        return q.getUpdatedAt() != null
                && q.getUpdatedAt().isAfter(Instant.now().minus(minutes, ChronoUnit.MINUTES));
    }
}
