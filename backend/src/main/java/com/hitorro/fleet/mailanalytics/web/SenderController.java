/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.analytics.SenderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/senders")
public class SenderController {

    private final SenderService service;

    public SenderController(SenderService service) { this.service = service; }

    @GetMapping("/{email}")
    public Map<String, Object> profile(@PathVariable String email,
                                       @RequestParam(required = false) Instant from,
                                       @RequestParam(required = false) Instant to) {
        return service.profile(email, from, to);
    }

    @GetMapping("/{email}/messages")
    public JsonNode messages(@PathVariable String email,
                             @RequestParam(required = false) Instant from,
                             @RequestParam(required = false) Instant to,
                             @RequestParam(defaultValue = "0") int offset,
                             @RequestParam(defaultValue = "50") int limit) {
        return service.messages(email, from, to, offset, limit);
    }
}
