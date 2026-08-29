/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.analytics.DomainService;
import com.hitorro.fleet.mailanalytics.query.MailQueryShapers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/domains")
public class DomainController {

    private final DomainService service;

    public DomainController(DomainService service) { this.service = service; }

    @GetMapping("/{domain}")
    public Map<String, Object> profile(@PathVariable String domain,
                                       @RequestParam(required = false) Instant from,
                                       @RequestParam(required = false) Instant to) {
        return service.profile(domain, from, to);
    }

    @GetMapping("/{domain}/messages")
    public JsonNode messages(@PathVariable String domain,
                             @RequestParam(required = false) Instant from,
                             @RequestParam(required = false) Instant to,
                             @RequestParam(defaultValue = "0") int offset,
                             @RequestParam(defaultValue = "50") int limit) {
        return service.messages(domain, from, to, offset, limit);
    }

    @GetMapping("/{domain}/senders")
    public List<MailQueryShapers.TopN> senders(@PathVariable String domain,
                                               @RequestParam(required = false) Instant from,
                                               @RequestParam(required = false) Instant to,
                                               @RequestParam(defaultValue = "50") int limit) {
        return service.topSenders(domain, from, to, limit);
    }
}
