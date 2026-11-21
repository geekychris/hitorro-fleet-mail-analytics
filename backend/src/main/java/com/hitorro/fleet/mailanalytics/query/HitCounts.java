/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.query;

import tools.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.config.JsonValues;

/**
 * Exact hit counts from fleet-retrieval.
 *
 * <p>fleet-retrieval's {@code totalHits} is Lucene's {@code TopDocs.totalHits.value}
 * with the default threshold: past 1,000 matches it is only a lower bound
 * (e.g. 1001 for 2,476 matches), so counts above 1,000 came out wrong. Facet
 * counts are exact. A count query therefore also asks for the facet of a field
 * every mail document has, the boolean {@code read} (it has at most two values,
 * so its facet is never truncated), and the count is the sum of that facet's
 * values. Without the facet (an older index, or a document type without
 * {@code read}) it falls back to {@code totalHits}.</p>
 */
public final class HitCounts {

    /** Facet dimension every mail document carries. */
    public static final String COUNT_DIM = "read";

    private HitCounts() {}

    /** Adds the count facet to a query. */
    public static QueryBuilder countable(QueryBuilder qb) {
        return qb.facet(COUNT_DIM);
    }

    /** The exact number of hits in a response from a {@link #countable} query. */
    public static long exact(JsonNode resp) {
        if (resp == null) return 0;
        JsonNode values = resp.path("facets").path(COUNT_DIM).path("values");
        long total = JsonValues.asLong(resp.path("totalHits"), 0);
        if (values.isArray() && !values.isEmpty()) {
            long sum = 0;
            for (JsonNode v : values) sum += JsonValues.asLong(v.path("count"), 0);
            // A document without the field is not in the facet; never report
            // fewer than Lucene's lower bound.
            return Math.max(sum, total);
        }
        return total;
    }
}
