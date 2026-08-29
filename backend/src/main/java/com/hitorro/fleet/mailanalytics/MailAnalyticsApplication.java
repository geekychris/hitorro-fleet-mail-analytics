/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics;

import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * hitorro-fleet-mail-analytics — the mail "all seeing eye".
 *
 * <p>Sits beside hitorro-fleet-retrieval and owns everything that needs to
 * survive a restart: ingest watermarks, saved queries, alert rules,
 * firings, in-app inbox, reports, enrichment suggestions.
 *
 * <p>Two deployment modes selected by the Spring profile:
 * <ul>
 *   <li>{@code standalone} — embedded H2, in-process SimpleScheduler,
 *       Mac Mail SQLite ingest source. One jar, laptop-friendly.</li>
 *   <li>{@code clustered} — Postgres, IMAP ingest, external SMTP,
 *       K8s / Orion deploy alongside fleet-retrieval.</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(MailAnalyticsProperties.class)
@EnableScheduling
public class MailAnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MailAnalyticsApplication.class, args);
    }
}
