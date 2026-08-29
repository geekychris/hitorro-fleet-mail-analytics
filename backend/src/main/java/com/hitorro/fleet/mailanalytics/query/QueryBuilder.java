/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for the JSON body fleet-retrieval's /execute and
 * /search-multiple endpoints accept. Keeps callers away from Jackson
 * boilerplate. Terms are AND'd; use {@code raw()} to inject arbitrary
 * Lucene syntax fragments.
 */
public final class QueryBuilder {

    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    private String index;
    private final List<String> indexes = new ArrayList<>();
    private final List<String> queryParts = new ArrayList<>();
    private final List<String> facets = new ArrayList<>();
    private String lang = "en";
    private int offset = 0;
    private int limit = 20;
    private String sort;

    public static QueryBuilder over(String index) {
        QueryBuilder q = new QueryBuilder();
        q.index = index;
        return q;
    }

    public static QueryBuilder overMultiple(List<String> indexes) {
        QueryBuilder q = new QueryBuilder();
        q.indexes.addAll(indexes);
        return q;
    }

    public QueryBuilder text(String s) {
        if (s != null && !s.isBlank()) queryParts.add(s.trim());
        return this;
    }

    public QueryBuilder term(String field, String value) {
        if (field != null && !field.isBlank() && value != null && !value.isBlank()) {
            queryParts.add(field + ":\"" + escape(value) + "\"");
        }
        return this;
    }

    public QueryBuilder termIn(String field, List<String> values) {
        if (field == null || values == null || values.isEmpty()) return this;
        String joined = String.join(" OR ", values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> field + ":\"" + escape(v) + "\"").toList());
        if (!joined.isBlank()) queryParts.add("(" + joined + ")");
        return this;
    }

    public QueryBuilder booleanFlag(String field, boolean value) {
        queryParts.add(field + ":" + (value ? "true" : "false"));
        return this;
    }

    public QueryBuilder dateBetween(String field, Instant from, Instant to) {
        // Empty field name is the escape hatch for corpora that don't yet
        // have a searchable date projection — callers pass the configured
        // date field name (may be blank).
        if (field == null || field.isBlank()) return this;
        if (from == null && to == null) return this;
        String lo = from == null ? "*" : String.valueOf(from.toEpochMilli());
        String hi = to == null ? "*" : String.valueOf(to.toEpochMilli());
        queryParts.add(field + ":[" + lo + " TO " + hi + "]");
        return this;
    }

    public QueryBuilder raw(String fragment) {
        if (fragment != null && !fragment.isBlank()) queryParts.add(fragment.trim());
        return this;
    }

    public QueryBuilder facet(String dim) {
        if (dim != null && !dim.isBlank()) facets.add(dim);
        return this;
    }

    public QueryBuilder page(int offset, int limit) {
        this.offset = Math.max(0, offset);
        this.limit = Math.max(1, Math.min(500, limit));
        return this;
    }

    public QueryBuilder sort(String s) { this.sort = s; return this; }
    public QueryBuilder lang(String l) { if (l != null && !l.isBlank()) this.lang = l; return this; }

    /** Body for /api/retrieval/execute — wraps the query into the
     *  coordinator's stage-based JVS structure ({@code search} +
     *  {@code fetch} + {@code fixup}). See hitorro-search-ui's
     *  {@code JvsQueryShaper} for the canonical shape. */
    public JsonNode buildExecute() {
        ObjectNode body = NF.objectNode();
        body.put("indexName", index);
        body.put("lang", lang);
        ObjectNode query = body.putObject("query");

        ObjectNode search = query.putObject("search");
        search.put("query", assemble());
        search.put("offset", offset);
        search.put("limit", limit);
        search.put("lang", lang);
        if (!facets.isEmpty()) {
            ArrayNode fs = search.putArray("facets");
            for (String f : facets) fs.add(f);
        }
        if (sort != null && !sort.isBlank()) {
            ArrayNode sortArr = search.putArray("sort");
            for (String s : sort.split(",")) {
                String t = s.trim();
                if (t.isEmpty()) continue;
                int c = t.indexOf(':');
                ObjectNode k = sortArr.addObject();
                k.put("field", c < 0 ? t : t.substring(0, c).trim());
                k.put("direction", c < 0 ? "desc"
                        : ("asc".equalsIgnoreCase(t.substring(c + 1).trim()) ? "asc" : "desc"));
            }
        }
        // Always request full doc hydration + basic fixup so hits carry
        // the enriched JVS payload the UI + shapers expect.
        query.putObject("fetch");
        ObjectNode fixup = query.putObject("fixup");
        ArrayNode fixupTags = fixup.putArray("tags");
        fixupTags.add("basic");
        return body;
    }

    /** Body for /api/retrieval/search-multiple. */
    public JsonNode buildMulti() {
        ObjectNode body = NF.objectNode();
        ArrayNode idx = body.putArray("indexNames");
        if (!indexes.isEmpty()) indexes.forEach(idx::add);
        else if (index != null) idx.add(index);
        body.put("query", assemble());
        body.put("lang", lang);
        body.put("offset", offset);
        body.put("limit", limit);
        if (sort != null && !sort.isBlank()) body.put("sort", sort);
        if (!facets.isEmpty()) {
            ArrayNode fs = body.putArray("facets");
            for (String f : facets) fs.add(f);
        }
        return body;
    }

    private String assemble() {
        if (queryParts.isEmpty()) return "*:*";
        return String.join(" AND ", queryParts);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
