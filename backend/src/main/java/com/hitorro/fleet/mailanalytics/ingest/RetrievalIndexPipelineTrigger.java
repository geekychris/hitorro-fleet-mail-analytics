/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * Indexes an ingest batch straight into fleet-retrieval (standalone mode,
 * {@code POST /api/ingest/indexes/{index}/documents}) instead of handing it
 * to the mesh enrichment job. Use it where no mesh pipeline feeds
 * fleet-retrieval.
 *
 * <p>Documents are typed {@code mail_email_flat}
 * ({@code resources/types/mail_email_flat.json}, which must be in
 * fleet-retrieval's {@code $HT_BIN/config/types}). That type has no NLP
 * (mls) fields, so nothing needs OpenNLP models. Its identifier fields
 * ({@code sender_domain}, {@code sender_address}, {@code read},
 * {@code flagged}, {@code is_newsletter}, {@code mailbox_url}) are facetable,
 * and {@code date_received} is stored as epoch millis, so
 * {@code date_received.date_s} range filters work. Subject and preview are
 * also kept in the stored source as {@code title}/{@code body} mls entries
 * (with {@code clean} = the text) for the UI and summaries. They are not
 * enriched: there are no entities, segments or POS tags.</p>
 *
 * <p>Enable with {@code mailanalytics.pipeline.trigger=retrieval}.</p>
 */
@Component
@ConditionalOnProperty(name = "mailanalytics.pipeline.trigger", havingValue = "retrieval")
public class RetrievalIndexPipelineTrigger implements PipelineTrigger {

    private static final Logger log = LoggerFactory.getLogger(RetrievalIndexPipelineTrigger.class);
    private static final JsonNodeFactory NF = JsonNodeFactory.instance;

    private final String baseUrl;
    private final String index;
    private final String typeName;
    private final int chunk;
    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean indexEnsured;

    public RetrievalIndexPipelineTrigger(
            MailAnalyticsProperties props,
            @Value("${mailanalytics.pipeline.retrieval-type:mail_email_flat}") String typeName,
            @Value("${mailanalytics.pipeline.retrieval-chunk:200}") int chunk) {
        String b = props.getRetrieval().getBaseUrl();
        this.baseUrl = b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
        this.index = props.getRetrieval().getDefaultIndex();
        this.typeName = typeName;
        this.chunk = Math.max(1, chunk);
        this.timeout = Duration.ofMillis(Math.max(30_000, props.getRetrieval().getTimeoutMs()));
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public Result trigger(String sourceId, Path ndjsonBatch) {
        ensureIndex();
        int sent = 0;
        ArrayNode docs = NF.arrayNode();
        try (BufferedReader r = Files.newBufferedReader(ndjsonBatch, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                docs.add(toDocument(mapper.readTree(line), typeName));
                if (docs.size() >= chunk) {
                    sent += post(docs);
                    docs = NF.arrayNode();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("cannot read batch " + ndjsonBatch.getFileName() + ": " + e.getMessage(), e);
        }
        if (!docs.isEmpty()) sent += post(docs);
        log.info("retrieval trigger[{}]: indexed {} documents into {} ({})", sourceId, sent, index, typeName);
        return new Result(true, "retrieval-" + Instant.now().toEpochMilli(), "indexed " + sent + " into " + index);
    }

    /** Maps one NDJSON row (IngestOrchestrator's shape) to a typed document. */
    static ObjectNode toDocument(JsonNode row, String typeName) {
        ObjectNode d = NF.objectNode();
        d.put("type", typeName);
        String source = row.path("source").asText("mail");
        String did = row.path("source_cursor").asText();
        ObjectNode id = d.putObject("id");
        id.put("domain", source);
        id.put("did", did);
        id.put("id", source + "/" + did);

        String subject = row.path("subject").asText("");
        String preview = row.path("body_preview").asText("");
        d.put("subject", subject);
        d.put("body_preview", preview);
        d.set("title", mls(subject));
        if (!preview.isEmpty()) d.set("body", mls(preview));

        long millis = 0;
        String recv = row.path("date_received").asText("");
        if (!recv.isEmpty()) {
            try { millis = Instant.parse(recv).toEpochMilli(); } catch (RuntimeException ignore) { }
        }
        if (millis > 0) {
            d.put("date_received", millis);
            ObjectNode times = d.putObject("times");
            times.put("created", millis);
            times.put("date_received", millis);
        }
        d.put("message_id", row.path("message_id").asText(""));
        d.put("sender_address", row.path("sender_address").asText(""));
        d.put("sender_name", row.path("sender_name").asText(""));
        d.put("sender_domain", row.path("sender_domain").asText(""));
        d.put("mailbox_url", row.path("mailbox_url").asText(""));
        JsonNode f = row.path("flags");
        d.put("read", f.path("read").asBoolean(false));
        d.put("flagged", f.path("flagged").asBoolean(false));
        d.put("is_newsletter", f.path("newsletter").asBoolean(false));
        d.put("size_bytes", f.path("size_bytes").asLong(0));
        d.put("recipient_count", f.path("recipient_count").asLong(0));
        return d;
    }

    private static ObjectNode mls(String text) {
        ObjectNode o = NF.objectNode();
        ObjectNode e = o.putArray("mls").addObject();
        e.put("lang", "en");
        e.put("text", text);
        e.put("clean", text);
        return o;
    }

    private void ensureIndex() {
        if (indexEnsured) return;
        ObjectNode body = NF.objectNode();
        body.put("name", index);
        body.put("typeName", typeName);
        JsonNode resp = send("/api/ingest/indexes", body);
        if (!resp.path("success").asBoolean(false)) {
            throw new RuntimeException("fleet-retrieval refused to create index " + index + ": " + resp.path("error").asText(""));
        }
        indexEnsured = true;
    }

    private int post(ArrayNode docs) {
        JsonNode resp = send("/api/ingest/indexes/" + URLEncoder.encode(index, StandardCharsets.UTF_8) + "/documents", docs);
        if (!resp.path("success").asBoolean(false)) {
            throw new RuntimeException("fleet-retrieval ingest failed: " + resp.path("error").asText("unknown"));
        }
        return resp.path("indexed").asInt(docs.size());
    }

    private JsonNode send(String path, JsonNode body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("fleet-retrieval " + path.replaceAll("/documents$", "") + " returned HTTP " + resp.statusCode());
            }
            return mapper.readTree(resp.body());
        } catch (IOException e) {
            throw new RuntimeException("fleet-retrieval unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        }
    }
}
