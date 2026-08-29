/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.query.QueryBuilder;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Full-message fetch by id — used by the Threads page to expand a
 * message body inline for cross-referencing against the LLM summary.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final RetrievalClient client;
    private final MailAnalyticsProperties props;

    public MessageController(RetrievalClient client, MailAnalyticsProperties props) {
        this.client = client;
        this.props = props;
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> get(@PathVariable String id) {
        QueryBuilder qb = QueryBuilder.over(props.getRetrieval().getDefaultIndex())
                .term("id.id", id)
                .page(0, 1);
        JsonNode resp = client.execute(qb.buildExecute());
        JsonNode doc = resp == null ? null : resp.path("documents").path(0);
        if (doc == null || doc.isMissingNode() || doc.isNull()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(doc);
    }
}
