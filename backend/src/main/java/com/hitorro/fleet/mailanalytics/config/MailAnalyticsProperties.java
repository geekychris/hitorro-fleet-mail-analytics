/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * All tunables live here under the {@code mailanalytics.*} prefix.
 * application.yml supplies defaults; profile files override per-mode.
 */
@ConfigurationProperties(prefix = "mailanalytics")
public class MailAnalyticsProperties {

    /** {@code standalone} or {@code clustered}. Informational only —
     *  actual datasource / delivery wiring is driven by Spring profiles. */
    private String mode = "standalone";

    private final Retrieval retrieval = new Retrieval();
    private final Pipelines pipelines = new Pipelines();
    private final Ingest ingest = new Ingest();
    private final Delivery delivery = new Delivery();
    private final Cors cors = new Cors();
    private final Ai ai = new Ai();

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Retrieval getRetrieval() { return retrieval; }
    public Pipelines getPipelines() { return pipelines; }
    public Ingest getIngest() { return ingest; }
    public Delivery getDelivery() { return delivery; }
    public Cors getCors() { return cors; }
    public Ai getAi() { return ai; }

    /** Ollama binding for the summarizer. */
    public static class Ai {
        private String ollamaUrl = "http://localhost:11434";
        private String model = "llama3";
        private int timeoutSeconds = 120;
        public String getOllamaUrl() { return ollamaUrl; }
        public void setOllamaUrl(String ollamaUrl) { this.ollamaUrl = ollamaUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    /** Where hitorro-fleet-retrieval lives. */
    public static class Retrieval {
        private String baseUrl = "http://localhost:8095";
        private int timeoutMs = 15_000;
        /** Default index name to search when the caller doesn't specify. */
        private String defaultIndex = "mail";
        /** Physical Lucene field used for date-range filters. Empty ⇒
         *  filters are dropped (right when the index doesn't have a
         *  searchable date projection). Set to {@code date_received}
         *  once the pipeline groovy step populates a top-level field. */
        private String dateField = "";
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public String getDefaultIndex() { return defaultIndex; }
        public void setDefaultIndex(String defaultIndex) { this.defaultIndex = defaultIndex; }
        public String getDateField() { return dateField; }
        public void setDateField(String dateField) { this.dateField = dateField == null ? "" : dateField; }
    }

    /** Pipeline runtime paths. Analytics writes NDJSON here for the mesh
     *  pipeline to consume; mesh writes indexes + KV back. */
    public static class Pipelines {
        /** Root of the pipelines home. Mesh pipelines write
         *  {@code $home/lucene/*}, {@code $home/kv/*}. */
        private String home = System.getProperty("user.home") + "/hthome/pipelines";
        /** Where analytics drops NDJSON batches for the ingest pipeline. */
        private String hotDir = System.getProperty("user.home") + "/hthome/mailanalytics/hot";
        /** Classpath resource or filesystem path to the ingest job template. */
        private String ingestJobYaml = "classpath:jobs/mail-enrich-from-ndjson.yaml";
        public String getHome() { return home; }
        public void setHome(String home) { this.home = home; }
        public String getHotDir() { return hotDir; }
        public void setHotDir(String hotDir) { this.hotDir = hotDir; }
        public String getIngestJobYaml() { return ingestJobYaml; }
        public void setIngestJobYaml(String ingestJobYaml) { this.ingestJobYaml = ingestJobYaml; }
    }

    /** Ingest source definitions. Each entry becomes a MailSource bean. */
    public static class Ingest {
        /** Default cron for delta pulls. Overridable per-source. */
        private String defaultCron = "0 */5 * * * *";
        /** Max messages pulled per batch. */
        private int batchSize = 500;
        /** How far back to look on first backfill if none specified. */
        private Duration backfillHorizon = Duration.ofDays(365);
        /** Named sources. Kind = {@code sqlite} or {@code imap}. */
        private List<Source> sources = new ArrayList<>();
        public String getDefaultCron() { return defaultCron; }
        public void setDefaultCron(String defaultCron) { this.defaultCron = defaultCron; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public Duration getBackfillHorizon() { return backfillHorizon; }
        public void setBackfillHorizon(Duration backfillHorizon) { this.backfillHorizon = backfillHorizon; }
        public List<Source> getSources() { return sources; }
        public void setSources(List<Source> sources) { this.sources = sources; }

        public static class Source {
            /** Stable id used in URLs + watermark rows. */
            private String id;
            /** {@code sqlite} or {@code imap}. */
            private String kind;
            private boolean enabled = true;
            private String cron;
            /** Free-form per-kind config: for sqlite → {@code path}; for imap →
             *  {@code host, port, user, password, ssl, folder}. */
            private Map<String, String> config = Map.of();
            public String getId() { return id; }
            public void setId(String id) { this.id = id; }
            public String getKind() { return kind; }
            public void setKind(String kind) { this.kind = kind; }
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getCron() { return cron; }
            public void setCron(String cron) { this.cron = cron; }
            public Map<String, String> getConfig() { return config; }
            public void setConfig(Map<String, String> config) { this.config = config; }
        }
    }

    public static class Delivery {
        private final Email email = new Email();
        private final Webhook webhook = new Webhook();
        public Email getEmail() { return email; }
        public Webhook getWebhook() { return webhook; }

        public static class Email {
            private boolean enabled = false;
            private String host = "localhost";
            private int port = 25;
            private String user;
            private String password;
            private boolean starttls = false;
            private String from = "mail-analytics@localhost";
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getHost() { return host; }
            public void setHost(String host) { this.host = host; }
            public int getPort() { return port; }
            public void setPort(int port) { this.port = port; }
            public String getUser() { return user; }
            public void setUser(String user) { this.user = user; }
            public String getPassword() { return password; }
            public void setPassword(String password) { this.password = password; }
            public boolean isStarttls() { return starttls; }
            public void setStarttls(boolean starttls) { this.starttls = starttls; }
            public String getFrom() { return from; }
            public void setFrom(String from) { this.from = from; }
        }

        public static class Webhook {
            private int timeoutMs = 10_000;
            private int maxRetries = 3;
            public int getTimeoutMs() { return timeoutMs; }
            public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
            public int getMaxRetries() { return maxRetries; }
            public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        }
    }

    public static class Cors {
        /** {@code "*"} = dev-open, empty = same-origin, else comma-separated. */
        private String allowedOrigins = "*";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }
}
