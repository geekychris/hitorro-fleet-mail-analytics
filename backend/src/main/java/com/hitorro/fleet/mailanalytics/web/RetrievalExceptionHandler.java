/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Translates upstream errors from hitorro-fleet-retrieval into clean
 * responses for the browser. Two shapes:
 * <ul>
 *   <li>{@code 400 "Index not found"} → {@code 200} with an empty payload.
 *     Common pre-first-ingest state — the UI shouldn't scream about it.</li>
 *   <li>Any other upstream error → {@code 502 Bad Gateway} with the
 *     upstream status + body preserved.</li>
 * </ul>
 */
@RestControllerAdvice
public class RetrievalExceptionHandler {

    @ExceptionHandler(RetrievalClient.RetrievalException.class)
    public ResponseEntity<Map<String, Object>> handle(RetrievalClient.RetrievalException e) {
        if (e.upstreamStatus == 400 && e.upstreamBody != null && e.upstreamBody.contains("Index not found")) {
            return ResponseEntity.ok(Map.of(
                    "totalHits", 0,
                    "documents", java.util.List.of(),
                    "facets", Map.of(),
                    "note", "index not yet created — ingest hasn't produced any docs"));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "fleet-retrieval upstream error",
                "upstreamStatus", e.upstreamStatus,
                "upstreamBody", e.upstreamBody));
    }
}
