/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.query;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryBuilderTest {

    @Test
    void empty_query_produces_match_all_wrapped_in_search_stage() {
        JsonNode b = QueryBuilder.over("mail").buildExecute();
        assertThat(b.get("indexName").asText()).isEqualTo("mail");
        JsonNode search = b.get("query").get("search");
        assertThat(search.get("query").asText()).isEqualTo("*:*");
        assertThat(search.get("offset").asInt()).isZero();
        assertThat(search.get("limit").asInt()).isEqualTo(20);
        // fetch + fixup stages always emitted for full doc hydration.
        assertThat(b.get("query").has("fetch")).isTrue();
        assertThat(b.get("query").get("fixup").get("tags").get(0).asText()).isEqualTo("basic");
    }

    @Test
    void terms_are_ANDed_and_escaped() {
        JsonNode b = QueryBuilder.over("mail")
                .term("sender_domain", "acme.com")
                .term("subject", "a \"weird\" one")
                .buildExecute();
        String q = b.get("query").get("search").get("query").asText();
        assertThat(q).contains("sender_domain:\"acme.com\"");
        assertThat(q).contains("subject:\"a \\\"weird\\\" one\"");
        assertThat(q).contains(" AND ");
    }

    @Test
    void date_between_open_lower_bound_uses_star() {
        Instant to = Instant.parse("2026-01-01T00:00:00Z");
        JsonNode b = QueryBuilder.over("mail").dateBetween("date_received", null, to).buildExecute();
        assertThat(b.get("query").get("search").get("query").asText())
                .contains("date_received:[* TO " + to.toEpochMilli() + "]");
    }

    @Test
    void term_in_produces_OR_group() {
        JsonNode b = QueryBuilder.over("mail")
                .termIn("sender_domain", List.of("a.com", "b.com"))
                .buildExecute();
        assertThat(b.get("query").get("search").get("query").asText())
                .isEqualTo("(sender_domain:\"a.com\" OR sender_domain:\"b.com\")");
    }

    @Test
    void facets_serialize_as_array_inside_search_stage() {
        JsonNode b = QueryBuilder.over("mail").facet("sender_domain").facet("read").buildExecute();
        JsonNode facets = b.get("query").get("search").get("facets");
        assertThat(facets.isArray()).isTrue();
        assertThat(facets.size()).isEqualTo(2);
    }

    @Test
    void sort_serializes_as_field_direction_objects_inside_search() {
        JsonNode b = QueryBuilder.over("mail").sort("date_received:desc,sender_domain:asc").buildExecute();
        JsonNode sort = b.get("query").get("search").get("sort");
        assertThat(sort.isArray()).isTrue();
        assertThat(sort.size()).isEqualTo(2);
        assertThat(sort.get(0).get("field").asText()).isEqualTo("date_received");
        assertThat(sort.get(0).get("direction").asText()).isEqualTo("desc");
        assertThat(sort.get(1).get("direction").asText()).isEqualTo("asc");
    }

    @Test
    void multi_uses_indexNames() {
        JsonNode b = QueryBuilder.overMultiple(List.of("mail", "mail-archive")).text("foo").buildMulti();
        assertThat(b.get("indexNames").isArray()).isTrue();
        assertThat(b.get("indexNames").size()).isEqualTo(2);
    }
}
