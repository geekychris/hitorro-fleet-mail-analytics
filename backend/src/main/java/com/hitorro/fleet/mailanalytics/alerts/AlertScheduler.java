/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.alerts;

import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import com.hitorro.fleet.mailanalytics.repo.AlertRuleRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/** Registers every enabled AlertRule on the shared TaskScheduler. Callers
 *  hit refresh() after CRUD mutations to rebind. */
@Component
public class AlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertScheduler.class);

    private final AlertRuleRepository rules;
    private final AlertEngine engine;
    private final TaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> handles = new HashMap<>();

    public AlertScheduler(AlertRuleRepository rules, AlertEngine engine, TaskScheduler taskScheduler) {
        this.rules = rules;
        this.engine = engine;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public synchronized void refresh() {
        handles.values().forEach(h -> h.cancel(false));
        handles.clear();
        for (AlertRule r : rules.findByEnabledTrue()) {
            try {
                ScheduledFuture<?> h = taskScheduler.schedule(
                        () -> {
                            try { engine.evaluate(r.getId()); }
                            catch (Exception e) { log.warn("alert[{}] evaluate failed: {}", r.getId(), e.getMessage()); }
                        },
                        new CronTrigger(r.getCron()));
                handles.put(r.getId(), h);
                log.info("scheduled alert[{}] '{}' cron='{}'", r.getId(), r.getName(), r.getCron());
            } catch (Exception e) {
                log.warn("scheduling alert[{}] failed — cron '{}' invalid?: {}", r.getId(), r.getCron(), e.getMessage());
            }
        }
    }
}
