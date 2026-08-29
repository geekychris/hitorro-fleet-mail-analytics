/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.reports;

import com.hitorro.fleet.mailanalytics.entities.Report;
import com.hitorro.fleet.mailanalytics.repo.ReportRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

@Component
public class ReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportScheduler.class);

    private final ReportRepository repo;
    private final ReportEngine engine;
    private final TaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> handles = new HashMap<>();

    public ReportScheduler(ReportRepository repo, ReportEngine engine, TaskScheduler taskScheduler) {
        this.repo = repo;
        this.engine = engine;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public synchronized void refresh() {
        handles.values().forEach(h -> h.cancel(false));
        handles.clear();
        for (Report r : repo.findByEnabledTrue()) {
            if (r.getCron() == null || r.getCron().isBlank()) continue;
            try {
                ScheduledFuture<?> h = taskScheduler.schedule(
                        () -> {
                            try { engine.runNow(r.getId()); }
                            catch (Exception e) { log.warn("report[{}] scheduled run failed: {}", r.getId(), e.getMessage()); }
                        },
                        new CronTrigger(r.getCron()));
                handles.put(r.getId(), h);
                log.info("scheduled report[{}] '{}' cron='{}'", r.getId(), r.getName(), r.getCron());
            } catch (Exception e) {
                log.warn("scheduling report[{}] failed: {}", r.getId(), e.getMessage());
            }
        }
    }
}
