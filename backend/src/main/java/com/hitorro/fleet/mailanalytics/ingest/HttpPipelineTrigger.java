/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads a job YAML template, substitutes {@code __NDJSON_PATH__} with the
 * batch path, POSTs to a running mesh-driver's {@code /mesh/jobs/run}.
 * Activated when {@code mailanalytics.pipeline.trigger=http}.
 */
@Component
@ConditionalOnProperty(name = "mailanalytics.pipeline.trigger", havingValue = "http")
public class HttpPipelineTrigger implements PipelineTrigger {

    private static final Logger log = LoggerFactory.getLogger(HttpPipelineTrigger.class);
    private static final String NDJSON_PLACEHOLDER = "__NDJSON_PATH__";
    private static final String SOURCE_ID_PLACEHOLDER = "__SOURCE_ID__";

    private final WebClient client;
    private final String jobTemplatePath;

    public HttpPipelineTrigger(
            @Value("${mailanalytics.pipeline.driver-url:http://localhost:8090}") String driverUrl,
            @Value("${mailanalytics.pipeline.job-template:classpath:jobs/mail-enrich-from-ndjson.yaml}") String jobTemplatePath) {
        this.client = WebClient.builder().baseUrl(driverUrl).build();
        this.jobTemplatePath = jobTemplatePath;
    }

    @Override
    public Result trigger(String sourceId, Path ndjsonBatch) {
        try {
            String template = loadTemplate();
            String yaml = template
                    .replace(NDJSON_PLACEHOLDER, ndjsonBatch.toAbsolutePath().toString())
                    .replace(SOURCE_ID_PLACEHOLDER, sourceId);
            JsonNode resp = client.post()
                    .uri("/mesh/jobs/run")
                    .contentType(MediaType.valueOf("application/x-yaml"))
                    .bodyValue(yaml)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            String jobId = resp != null && resp.has("jobId") ? resp.get("jobId").asText() : "unknown";
            return new Result(true, jobId, "accepted by mesh-driver");
        } catch (IOException e) {
            log.warn("http pipeline trigger failed to load template {}: {}", jobTemplatePath, e.getMessage());
            throw new RuntimeException("cannot load job template " + jobTemplatePath, e);
        } catch (Exception e) {
            log.warn("http pipeline trigger to mesh-driver failed: {}", e.getMessage());
            throw new RuntimeException("mesh-driver invocation failed", e);
        }
    }

    private String loadTemplate() throws IOException {
        if (jobTemplatePath.startsWith("classpath:")) {
            String cp = jobTemplatePath.substring("classpath:".length());
            try (var in = getClass().getClassLoader().getResourceAsStream(cp)) {
                if (in == null) throw new IOException("classpath resource not found: " + cp);
                return new String(in.readAllBytes());
            }
        }
        return Files.readString(Path.of(jobTemplatePath));
    }
}
