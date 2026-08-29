/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.analytics.TopicService;
import com.hitorro.fleet.mailanalytics.query.MailQueryShapers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicService service;

    public TopicController(TopicService service) { this.service = service; }

    @GetMapping("/entities")
    public Map<String, List<MailQueryShapers.TopN>> entities(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "20") int limit) {
        return service.entityRollup(from, to, limit);
    }
}
