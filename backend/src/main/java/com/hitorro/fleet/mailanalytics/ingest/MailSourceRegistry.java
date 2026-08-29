/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Materializes {@link MailSource} beans from configured
 *  {@code mailanalytics.ingest.sources[]}. Keeps them in a lookup map
 *  keyed by id. */
@Component
public class MailSourceRegistry {

    private static final Logger log = LoggerFactory.getLogger(MailSourceRegistry.class);

    private final MailAnalyticsProperties props;
    private final Map<String, MailSource> sources = new LinkedHashMap<>();

    public MailSourceRegistry(MailAnalyticsProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        for (var s : props.getIngest().getSources()) {
            if (!s.isEnabled()) { log.info("mail source '{}' disabled", s.getId()); continue; }
            MailSource src = switch (s.getKind()) {
                case "sqlite" -> new SqliteMailSource(s.getId(), s.getConfig().getOrDefault("path", ""));
                case "imap" -> new ImapMailSource(s.getId(), s.getConfig());
                default -> {
                    log.warn("unknown mail source kind '{}' — skipping", s.getKind());
                    yield null;
                }
            };
            if (src != null) {
                sources.put(s.getId(), src);
                log.info("registered mail source id='{}' kind={}", s.getId(), s.getKind());
            }
        }
    }

    public Collection<MailSource> all() { return sources.values(); }
    public MailSource get(String id) { return sources.get(id); }
}
