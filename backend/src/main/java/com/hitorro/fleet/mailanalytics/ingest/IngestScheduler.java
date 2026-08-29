/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/** Registers each enabled ingest source on the shared {@link TaskScheduler}
 *  with its configured cron. Per-source lock guards against overlap when
 *  a run takes longer than the interval. */
@Component
public class IngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);

    private final MailAnalyticsProperties props;
    private final MailSourceRegistry registry;
    private final IngestOrchestrator orchestrator;
    private final TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> handles = new HashMap<>();
    private final Map<String, ReentrantLock> locks = new HashMap<>();

    public IngestScheduler(MailAnalyticsProperties props,
                           MailSourceRegistry registry,
                           IngestOrchestrator orchestrator,
                           TaskScheduler taskScheduler) {
        this.props = props;
        this.registry = registry;
        this.orchestrator = orchestrator;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    void init() {
        for (var s : props.getIngest().getSources()) {
            if (!s.isEnabled()) continue;
            MailSource src = registry.get(s.getId());
            if (src == null) continue;
            String cron = s.getCron() == null || s.getCron().isBlank()
                    ? props.getIngest().getDefaultCron() : s.getCron();
            schedule(src.id(), cron);
        }
    }

    public synchronized void schedule(String sourceId, String cron) {
        cancel(sourceId);
        locks.putIfAbsent(sourceId, new ReentrantLock());
        ReentrantLock lock = locks.get(sourceId);
        Runnable task = () -> {
            if (!lock.tryLock()) {
                log.debug("skip overlap for {}", sourceId);
                return;
            }
            try {
                MailSource src = registry.get(sourceId);
                if (src == null) return;
                orchestrator.runOnce(src);
            } catch (RuntimeException e) {
                log.warn("ingest[{}] failed: {}", sourceId, e.getMessage());
            } finally { lock.unlock(); }
        };
        ScheduledFuture<?> handle = taskScheduler.schedule(task, new CronTrigger(cron));
        handles.put(sourceId, handle);
        log.info("scheduled ingest[{}] cron='{}'", sourceId, cron);
    }

    public synchronized void cancel(String sourceId) {
        ScheduledFuture<?> h = handles.remove(sourceId);
        if (h != null) h.cancel(false);
    }
}
