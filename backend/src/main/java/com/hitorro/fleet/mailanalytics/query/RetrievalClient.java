/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.query;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Adapter around the hitorro-fleet-retrieval REST surface. All calls
 * block via {@code .block()} — we run on the servlet stack and use
 * WebClient only for its convenient timeouts + pooling. Errors are
 * surfaced as {@link RetrievalException} carrying upstream status + body.
 */
@Component
public class RetrievalClient {

    private final WebClient client;

    public RetrievalClient(WebClient fleetWebClient) {
        this.client = fleetWebClient;
    }

    /** GET /api/retrieval/health. */
    public JsonNode health() { return get("/api/retrieval/health"); }

    /** GET /api/retrieval/indexes. */
    public JsonNode indexes() { return get("/api/retrieval/indexes"); }

    /** POST /api/retrieval/execute — single-index coordinator. */
    public JsonNode execute(JsonNode body) { return post("/api/retrieval/execute", body); }

    /** POST /api/retrieval/search-multiple — cross-index federation. */
    public JsonNode searchMultiple(JsonNode body) { return post("/api/retrieval/search-multiple", body); }

    /** GET /api/retrieval/documents/{index}/{key} — one full KV doc. */
    public JsonNode document(String indexName, String key) {
        return get("/api/retrieval/documents/" + enc(indexName) + "/" + enc(key));
    }

    /** GET /api/retrieval/fields/{index} — Lucene FieldInfos for the index. */
    public JsonNode fields(String indexName) {
        return get("/api/retrieval/fields/" + enc(indexName));
    }

    // -------------------------------------------------------------- helpers

    private JsonNode get(String path) {
        try {
            return client.get().uri(path)
                    .retrieve().bodyToMono(JsonNode.class).block();
        } catch (WebClientResponseException e) {
            throw new RetrievalException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }

    private JsonNode post(String path, JsonNode body) {
        try {
            return client.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve().bodyToMono(JsonNode.class).block();
        } catch (WebClientResponseException e) {
            throw new RetrievalException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static class RetrievalException extends RuntimeException {
        public final int upstreamStatus;
        public final String upstreamBody;
        public RetrievalException(int status, String body, Throwable cause) {
            super("fleet-retrieval " + status + ": " + body, cause);
            this.upstreamStatus = status;
            this.upstreamBody = body;
        }
    }
}
