/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.analytics.ThreadClusteringService;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that analytics' SummaryService no longer talks to Ollama
 * directly — it routes summarization through fleet-retrieval's
 * {@code POST /api/retrieval/summarize} endpoint. Stubs the fleet
 * endpoint with a JDK {@link HttpServer} on a random port so the test
 * asserts the actual wire request (URL, body, style field) rather
 * than mock-verifying an in-process WebClient.
 */
class SummaryServiceTest {

    private HttpServer stubFleet;
    private String stubBaseUrl;
    private final AtomicReference<String> lastRequestPath = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void startStub() throws IOException {
        stubFleet = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // /api/retrieval/summary-styles → return a canned catalogue
        stubFleet.createContext("/api/retrieval/summary-styles", ex -> {
            byte[] body = "[{\"id\":\"BRIEF\"},{\"id\":\"CONTRIBUTIONS\"}]".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        // /api/retrieval/summarize → record the request + return a canned summary
        stubFleet.createContext("/api/retrieval/summarize", ex -> {
            lastRequestPath.set(ex.getRequestURI().getPath());
            lastRequestBody.set(drain(ex.getRequestBody()));
            byte[] body = ("{\"summary\":\"OK-STUB\",\"style\":\"BRIEF\",\"elapsedMs\":42}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        stubFleet.start();
        stubBaseUrl = "http://127.0.0.1:" + stubFleet.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        if (stubFleet != null) stubFleet.stop(0);
    }

    @Test
    void listStyles_prefers_fleet_response_over_local_fallback() {
        SummaryService svc = newService();
        List<Map<String, String>> styles = svc.listStyles();
        assertThat(styles).extracting(m -> m.get("id"))
                .containsExactly("BRIEF", "CONTRIBUTIONS");
    }

    @Test
    void summarizeThread_posts_to_fleet_with_style_and_rendered_text() throws Exception {
        // The service asks ThreadClusteringService for the cluster; return a
        // single 2-message cluster so we exercise the render+post path without
        // hitting a real index.
        ThreadClusteringService threads = mock(ThreadClusteringService.class);
        RetrievalClient retrieval = mock(RetrievalClient.class);
        when(retrieval.execute(any())).thenReturn(mapper.createObjectNode());

        List<ThreadClusteringService.Cluster> clusters = new ArrayList<>();
        // Empty-message cluster is enough — we're proving the routing shape
        // (URL, style, subject line) not the render fidelity. Populating
        // messages needs Cluster.add() which is package-private; a
        // separate test in the analytics package covers render output.
        ThreadClusteringService.Cluster c = new ThreadClusteringService.Cluster("k1", "SUBJECT-1");
        clusters.add(c);
        when(threads.clusters(any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(clusters);

        MailAnalyticsProperties props = new MailAnalyticsProperties();
        props.getRetrieval().setBaseUrl(stubBaseUrl);
        props.getRetrieval().setDefaultIndex("mail");

        SummaryService svc = new SummaryService(retrieval, threads, props);
        SummaryService.Result r = svc.summarizeThread("k1", SummaryStyle.BRIEF, null, null, null);

        assertThat(r.ok()).isTrue();
        assertThat(r.payload().get("summary")).isEqualTo("OK-STUB");
        assertThat(r.payload().get("style")).isEqualTo("BRIEF");
        assertThat(r.payload().get("subject")).isEqualTo("SUBJECT-1");
        assertThat(r.payload().get("messageCount")).isEqualTo(0);

        // Wire assertions — the request must have hit the shared endpoint
        // with a JSON body carrying the text + style. Rendered text should
        // include the subject line so we know the render helper ran.
        assertThat(lastRequestPath.get()).isEqualTo("/api/retrieval/summarize");
        JsonNode body = mapper.readTree(lastRequestBody.get());
        assertThat(body.get("style").asText()).isEqualTo("BRIEF");
        assertThat(body.get("text").asText()).contains("Subject: SUBJECT-1");
    }

    @Test
    void summarizeThread_reports_notFound_when_cluster_missing() {
        ThreadClusteringService threads = mock(ThreadClusteringService.class);
        when(threads.clusters(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        RetrievalClient retrieval = mock(RetrievalClient.class);
        MailAnalyticsProperties props = new MailAnalyticsProperties();
        props.getRetrieval().setBaseUrl(stubBaseUrl);

        SummaryService svc = new SummaryService(retrieval, threads, props);
        SummaryService.Result r = svc.summarizeThread("nonexistent-key", SummaryStyle.BRIEF, null, null, null);
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).contains("no thread with subject key");
    }

    private SummaryService newService() {
        MailAnalyticsProperties props = new MailAnalyticsProperties();
        props.getRetrieval().setBaseUrl(stubBaseUrl);
        return new SummaryService(mock(RetrievalClient.class),
                mock(ThreadClusteringService.class), props);
    }

    private static String drain(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        in.transferTo(b);
        return b.toString(StandardCharsets.UTF_8);
    }
}
