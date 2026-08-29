/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.query.MailQueryShapers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final MailQueryShapers shapers;

    public SearchController(MailQueryShapers shapers) { this.shapers = shapers; }

    @GetMapping("/mail")
    public JsonNode search(@RequestParam(required = false, defaultValue = "") String q,
                           @RequestParam(required = false) Instant from,
                           @RequestParam(required = false) Instant to,
                           @RequestParam(defaultValue = "0") int offset,
                           @RequestParam(defaultValue = "20") int limit,
                           @RequestParam(required = false, defaultValue = "date_received.date_s:desc") String sort) {
        return shapers.search(q, from, to, offset, limit, sort);
    }
}
