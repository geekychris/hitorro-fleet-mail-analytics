/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.web;

import com.hitorro.fleet.mailanalytics.analytics.ThreadClusteringService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/clusters")
public class ClusterController {

    private final ThreadClusteringService threads;

    public ClusterController(ThreadClusteringService threads) { this.threads = threads; }

    @GetMapping("/threads")
    public List<ThreadClusteringService.Cluster> threads(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "500") int scanLimit) {
        return threads.clusters(from, to, scanLimit);
    }
}
