/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RetrievalIndexPipelineTrigger against a stub fleet-retrieval ingest API. */
class RetrievalIndexPipelineTriggerTest {

    static final ObjectMapper M = new ObjectMapper();
    HttpServer server;
    final List<String> paths = new CopyOnWriteArrayList<>();
    final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    volatile String docsReply = null;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            paths.add(ex.getRequestURI().getPath());
            JsonNode n = M.readTree(body);
            bodies.add(n);
            String reply = ex.getRequestURI().getPath().endsWith("/documents")
                    ? (docsReply != null ? docsReply : "{\"success\":true,\"indexed\":" + n.size() + "}")
                    : "{\"success\":true}";
            byte[] b = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    RetrievalIndexPipelineTrigger trigger(int chunk) {
        MailAnalyticsProperties props = new MailAnalyticsProperties();
        props.getRetrieval().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return new RetrievalIndexPipelineTrigger(props, "mail_email_flat", chunk);
    }

    static String row(long cursor) {
        return "{\"source\":\"m1-mail\",\"source_cursor\":" + cursor + ",\"message_id\":\"m" + cursor + "@example.com\","
                + "\"date_received\":\"2026-11-19T10:00:00Z\",\"sender_address\":\"a@example.com\",\"sender_name\":\"A\","
                + "\"sender_domain\":\"example.com\",\"subject\":\"Synthetic " + cursor + "\",\"body_preview\":\"preview\","
                + "\"mailbox_url\":\"imap://x/INBOX\",\"flags\":{\"read\":true,\"flagged\":false,\"newsletter\":false,"
                + "\"size_bytes\":123,\"recipient_count\":2}}";
    }

    @Test
    void creates_the_typed_index_once_and_posts_documents_in_chunks(@TempDir Path tmp) throws Exception {
        Path batch = tmp.resolve("b.ndjson");
        Files.writeString(batch, row(1) + "\n" + row(2) + "\n\n" + row(3) + "\n");
        RetrievalIndexPipelineTrigger t = trigger(2);

        PipelineTrigger.Result r = t.trigger("m1-mail", batch);
        t.trigger("m1-mail", batch);

        assertThat(r.accepted()).isTrue();
        assertThat(r.detail()).isEqualTo("indexed 3 into mail");
        assertThat(paths).containsExactly("/api/ingest/indexes",
                "/api/ingest/indexes/mail/documents", "/api/ingest/indexes/mail/documents",
                "/api/ingest/indexes/mail/documents", "/api/ingest/indexes/mail/documents");
        assertThat(bodies.get(0).path("typeName").asText()).isEqualTo("mail_email_flat");
        assertThat(bodies.get(1).size()).isEqualTo(2);
        assertThat(bodies.get(2).size()).isEqualTo(1);
    }

    @Test
    void maps_a_row_to_the_flat_type() throws Exception {
        JsonNode d = RetrievalIndexPipelineTrigger.toDocument(M.readTree(row(42)), "mail_email_flat");
        assertThat(d.path("type").asText()).isEqualTo("mail_email_flat");
        assertThat(d.path("id").path("domain").asText()).isEqualTo("m1-mail");
        assertThat(d.path("id").path("did").asText()).isEqualTo("42");
        long millis = java.time.Instant.parse("2026-11-19T10:00:00Z").toEpochMilli();
        assertThat(d.path("date_received").asLong()).isEqualTo(millis);
        assertThat(d.path("times").path("date_received").asLong()).isEqualTo(millis);
        assertThat(d.path("sender_domain").asText()).isEqualTo("example.com");
        assertThat(d.path("read").asBoolean()).isTrue();
        assertThat(d.path("recipient_count").asLong()).isEqualTo(2);
        assertThat(d.path("title").path("mls").path(0).path("text").asText()).isEqualTo("Synthetic 42");
        assertThat(d.path("body").path("mls").path(0).path("clean").asText()).isEqualTo("preview");
    }

    @Test
    void the_type_definition_declares_facetable_fields_and_no_mls() throws Exception {
        JsonNode type = M.readTree(getClass().getResourceAsStream("/types/mail_email_flat.json"));
        assertThat(type.path("name").asText()).isEqualTo("mail_email_flat");
        assertThat(type.toString()).doesNotContain("core_mls");
        assertThat(type.toString()).contains("\"sender_domain\"", "\"date_received\"");
    }

    @Test
    void a_refused_batch_throws_so_the_watermark_holds(@TempDir Path tmp) throws Exception {
        Path batch = tmp.resolve("b.ndjson");
        Files.writeString(batch, row(1) + "\n");
        docsReply = "{\"success\":false,\"error\":\"boom\"}";
        assertThatThrownBy(() -> trigger(10).trigger("m1-mail", batch)).hasMessageContaining("boom");
    }
}
