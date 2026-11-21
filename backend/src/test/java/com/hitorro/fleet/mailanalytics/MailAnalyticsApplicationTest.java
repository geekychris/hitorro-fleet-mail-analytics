/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts the whole application offline: in-memory H2 migrated by Flyway, no
 * mail sources, and a local HTTP stub standing in for hitorro-fleet-retrieval.
 *
 * <p>The JSON tests pin the Spring Boot 4 / Jackson 2 setup: Spring MVC must
 * write a Jackson 2 {@code JsonNode} as plain JSON (Jackson 3 would write it as
 * a bean), and the WebClient to fleet-retrieval must encode and decode Jackson 2
 * {@code JsonNode} bodies.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mailanalytics-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MailAnalyticsApplicationTest {

    private static final String SEARCH_RESPONSE =
            "{\"total\":1,\"hits\":[{\"id\":\"m-1\",\"subject\":\"Quarterly report\",\"score\":1.5}]}";

    private static final ObjectMapper JSON = com.hitorro.fleet.mailanalytics.config.Jackson2Mappers.json();
    private static final AtomicReference<String> lastExecuteBody = new AtomicReference<>();
    private static HttpServer retrieval;
    private static Path tmp;

    @BeforeAll
    static void startRetrievalStub() throws IOException {
        tmp = Files.createTempDirectory("mailanalytics-test");
        retrieval = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        retrieval.createContext("/api/retrieval/execute", ex -> {
            lastExecuteBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(ex, SEARCH_RESPONSE);
        });
        // Bigger than the 256 KiB Reactor default: the fleet WebClient raises
        // the limit to 4 MiB, and that must survive the codec swap.
        retrieval.createContext("/api/retrieval/indexes", ex -> {
            StringBuilder sb = new StringBuilder("{\"indexes\":[");
            for (int i = 0; i < 20_000; i++) {
                if (i > 0) sb.append(',');
                sb.append("{\"name\":\"index-").append(i).append("\"}");
            }
            respond(ex, sb.append("]}").toString());
        });
        retrieval.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @AfterAll
    static void stopRetrievalStub() {
        if (retrieval != null) retrieval.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("mailanalytics.retrieval.base-url",
                () -> "http://127.0.0.1:" + retrieval.getAddress().getPort());
        registry.add("mailanalytics.pipelines.home", () -> tmp.resolve("pipelines").toString());
        registry.add("mailanalytics.pipelines.hot-dir", () -> tmp.resolve("hot").toString());
    }

    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired RetrievalClient retrievalClient;

    @Test
    void contextLoadsAndFlywayMigrates() {
        Flyway flyway = context.getBean(Flyway.class);
        assertThat(flyway.info().applied()).isNotEmpty();
    }

    @Test
    void springMvcUsesJackson3() {
        // Jackson 3 (Boot's mapper, configured for Jackson 2's format); the jackson2 bridge is gone.
        List<HttpMessageConverter<?>> converters =
                context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters();
        assertThat(converters).anyMatch(c -> c instanceof JacksonJsonHttpMessageConverter)
                .noneMatch(c -> c.getClass().getName().contains("Jackson2"));
    }

    @Test
    void healthEndpointReturnsJson() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.mode").value("standalone"));
    }

    @Test
    void savedQueryRoundTripsAndRunReturnsJsonNodeAsPlainJson() throws Exception {
        MvcResult created = mvc.perform(post("/api/saved-queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"reports\",\"dslJson\":\"{\\\"text\\\":\\\"quarterly\\\",\\\"limit\\\":5}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("reports"))
                // Jackson 2 with write-dates-as-timestamps=false: ISO-8601 strings
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn();
        long id = JSON.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult run = mvc.perform(post("/api/saved-queries/" + id + "/run"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        // The controller returns the Jackson 2 JsonNode it got from
        // fleet-retrieval; the body must be that JSON, byte for byte in content.
        String body = run.getResponse().getContentAsString();
        assertThat(JSON.readTree(body)).isEqualTo(JSON.readTree(SEARCH_RESPONSE));
        assertThat(body).doesNotContain("nodeType").doesNotContain("containerNode");

        // ...and the WebClient wrote the request JsonNode as JSON too.
        JsonNode sent = JSON.readTree(lastExecuteBody.get());
        assertThat(sent.isObject()).isTrue();
        assertThat(sent.toString()).contains("quarterly").doesNotContain("nodeType");
    }

    @Test
    void fleetWebClientDecodesLargeJsonNode() {
        JsonNode indexes = retrievalClient.indexes();
        assertThat(indexes.path("indexes").size()).isEqualTo(20_000);
        assertThat(indexes.path("indexes").get(19_999).path("name").asText()).isEqualTo("index-19999");
    }

    @org.springframework.beans.factory.annotation.Autowired
    tools.jackson.databind.json.JsonMapper bootJsonMapper;

    /**
     * Boot's mapper (Spring MVC's) writes what Boot's Jackson 2 mapper did for the JDK types Jackson 3
     * changed: Locale as {@code toString()} ("fr_CA", not the language tag "fr-CA"), Month by name (not
     * 10), byte[] in valueToTree as a binary (base64) node, and Instant as an ISO string.
     */
    @org.junit.jupiter.api.Test
    void bootMapperWritesJdkTypesAsJackson2() throws Exception {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("locale", java.util.Locale.CANADA_FRENCH);
        m.put("month", java.time.Month.NOVEMBER);
        m.put("bytes", new byte[]{1, 2, 3});
        m.put("at", java.time.Instant.parse("2026-01-02T03:04:05.123Z"));
        org.assertj.core.api.Assertions.assertThat(bootJsonMapper.writeValueAsString(m))
                .isEqualTo("{\"locale\":\"fr_CA\",\"month\":\"NOVEMBER\",\"bytes\":\"AQID\",\"at\":\"2026-01-02T03:04:05.123Z\"}");
        org.assertj.core.api.Assertions.assertThat(bootJsonMapper.valueToTree(new byte[]{1, 2, 3}).isBinary()).isTrue();
        org.assertj.core.api.Assertions.assertThat(bootJsonMapper.readValue("\"fr_CA\"", java.util.Locale.class))
                .isEqualTo(java.util.Locale.CANADA_FRENCH);
    }
}
