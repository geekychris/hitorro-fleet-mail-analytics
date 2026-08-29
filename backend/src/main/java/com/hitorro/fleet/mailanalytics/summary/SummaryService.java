/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.summary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.analytics.ThreadClusteringService;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.query.QueryBuilder;
import com.hitorro.fleet.mailanalytics.query.RetrievalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles grouped mail content (threads, entity-mentions) and hands it
 * to fleet-retrieval's {@code POST /api/retrieval/summarize} endpoint.
 * The style catalogue + prompt templates live in {@code hitorro-retrieval}'s
 * {@code SummaryStyles} class so the same variants are reusable from
 * every fleet member — this service is just the mail-side rendering layer.
 *
 * <p>Rendering (what "thread text" or "entity corpus" means for mail) is
 * analytics's concern. Prompt-driven synthesis is fleet-retrieval's.
 * New variants ship in {@code SummaryStyles.register(name, prompt)} —
 * this module picks them up automatically via
 * {@code GET /api/retrieval/summary-styles}.</p>
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final RetrievalClient retrieval;
    private final ThreadClusteringService threads;
    private final MailAnalyticsProperties props;
    private final WebClient fleet;
    private final ObjectMapper mapper = new ObjectMapper();

    public SummaryService(RetrievalClient retrieval,
                          ThreadClusteringService threads,
                          MailAnalyticsProperties props) {
        this.retrieval = retrieval;
        this.threads = threads;
        this.props = props;
        this.fleet = WebClient.builder()
                .baseUrl(props.getRetrieval().getBaseUrl().replaceAll("/+$", ""))
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    // ---- Public API ---------------------------------------------------

    public Result summarizeThread(String subjectKey, SummaryStyle style,
                                  Instant from, Instant to, String modelOverride) {
        List<ThreadClusteringService.Cluster> all = threads.clusters(from, to, 500);
        ThreadClusteringService.Cluster c = all.stream()
                .filter(x -> subjectKey.equals(x.key()))
                .findFirst().orElse(null);
        if (c == null) return Result.notFound("no thread with subject key '" + subjectKey + "'");
        String text = renderThread(c);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "thread");
        meta.put("subject", c.getSubject());
        meta.put("messageCount", c.messageCount());
        return callFleet(text, style, meta);
    }

    public Result summarizeEntity(String entity, String neKind, Instant from, Instant to,
                                  SummaryStyle style, String modelOverride) {
        QueryBuilder qb = QueryBuilder.over(props.getRetrieval().getDefaultIndex())
                .term("body.mls.clean.text_en_m", entity)
                .dateBetween("date_received.date_s", from, to)
                .sort("date_received.date_s:desc")
                .page(0, 20);
        JsonNode resp = retrieval.execute(qb.buildExecute());
        if (resp == null || !resp.path("documents").isArray() || resp.path("documents").size() == 0) {
            return Result.notFound("no messages mentioning '" + entity + "'");
        }
        String text = renderDocs(resp.path("documents"));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "entity");
        meta.put("entity", entity);
        if (neKind != null) meta.put("neKind", neKind);
        meta.put("messageCount", resp.path("documents").size());
        return callFleet(text, style, meta);
    }

    /** List styles by asking fleet-retrieval (canonical). Falls back to
     *  the local {@link SummaryStyle} enum when fleet-retrieval is
     *  unreachable so the UI dropdown still populates. */
    public List<Map<String, String>> listStyles() {
        try {
            JsonNode remote = fleet.get().uri("/api/retrieval/summary-styles")
                    .retrieve().bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(5));
            if (remote != null && remote.isArray() && remote.size() > 0) {
                List<Map<String, String>> out = new java.util.ArrayList<>();
                for (JsonNode n : remote) {
                    String id = n.path("id").asText();
                    out.add(Map.of(
                            "id", id,
                            "label", labelFor(id),
                            "description", descFor(id)));
                }
                return out;
            }
        } catch (Exception e) {
            log.debug("summary-styles from fleet-retrieval unavailable, using local fallback: {}",
                    e.getMessage());
        }
        List<Map<String, String>> out = new java.util.ArrayList<>();
        for (SummaryStyle s : SummaryStyle.values()) {
            out.add(Map.of(
                    "id", s.name(),
                    "label", labelFor(s.name()),
                    "description", descFor(s.name())));
        }
        return out;
    }

    // ---- Labels are UI concerns — kept here, not in the shared registry. ----

    private static String labelFor(String id) {
        return switch (id.toUpperCase()) {
            case "BRIEF"         -> "Brief summary";
            case "CONTRIBUTIONS" -> "Per-participant contributions";
            case "ACTION_ITEMS"  -> "Action items";
            case "DECISIONS"     -> "Decisions";
            case "SENTIMENT"     -> "Tone / sentiment";
            case "ENTITIES"      -> "Entities referenced";
            default              -> id;
        };
    }

    private static String descFor(String id) {
        return switch (id.toUpperCase()) {
            case "BRIEF"         -> "Two or three factual sentences.";
            case "CONTRIBUTIONS" -> "What each participant added to the conversation.";
            case "ACTION_ITEMS"  -> "Owners, tasks, and deadlines pulled out as a checklist.";
            case "DECISIONS"     -> "Decisions reached and who agreed.";
            case "SENTIMENT"     -> "Overall tone across the thread.";
            case "ENTITIES"      -> "People, orgs, products, places, dates mentioned.";
            default              -> "";
        };
    }

    // ---- Rendering (mail-specific — stays here) ----------------------

    private String renderThread(ThreadClusteringService.Cluster c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subject: ").append(c.getSubject()).append("\n\n");
        List<Map<String, Object>> msgs = new java.util.ArrayList<>(c.getMessages());
        msgs.sort((a, b) -> Long.compare(
                ((Number) a.getOrDefault("date", 0L)).longValue(),
                ((Number) b.getOrDefault("date", 0L)).longValue()));
        for (int i = 0; i < msgs.size(); i++) {
            Map<String, Object> m = msgs.get(i);
            sb.append("--- Message ").append(i + 1).append(" ---\n");
            sb.append("From: ").append(m.getOrDefault("sender", "?")).append("\n");
            long d = ((Number) m.getOrDefault("date", 0L)).longValue();
            if (d > 0) sb.append("Date: ").append(Instant.ofEpochMilli(d)).append("\n");
            String body = fetchBody(m.get("id"));
            if (body != null && !body.isBlank()) sb.append("\n").append(body).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    private String renderDocs(JsonNode docs) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (JsonNode d : docs) {
            sb.append("--- Message ").append(++i).append(" ---\n");
            sb.append("Subject: ").append(d.path("title").path("mls").path(0).path("text").asText("")).append("\n");
            sb.append("From: ").append(d.path("sender_address").asText("")).append("\n");
            long dt = d.path("date_received").asLong(0);
            if (dt > 0) sb.append("Date: ").append(Instant.ofEpochMilli(dt)).append("\n");
            String body = d.path("body").path("mls").path(0).path("clean").asText(
                    d.path("body").path("mls").path(0).path("text").asText(""));
            if (!body.isBlank()) sb.append("\n").append(body).append("\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Body isn't stored on the ThreadClusteringService message stubs; fetch by id. */
    private String fetchBody(Object idNode) {
        try {
            String id;
            if (idNode instanceof JsonNode jn) id = jn.path("id").asText(jn.asText());
            else id = String.valueOf(idNode);
            if (id == null || id.isBlank() || "null".equals(id)) return null;
            QueryBuilder qb = QueryBuilder.over(props.getRetrieval().getDefaultIndex())
                    .term("id.id", id)
                    .page(0, 1);
            JsonNode resp = retrieval.execute(qb.buildExecute());
            JsonNode doc = resp == null ? null : resp.path("documents").path(0);
            if (doc == null || doc.isMissingNode()) return null;
            String clean = doc.path("body").path("mls").path(0).path("clean").asText("");
            if (clean.isBlank()) clean = doc.path("body").path("mls").path(0).path("text").asText("");
            return clean;
        } catch (Exception e) {
            log.debug("fetchBody({}) failed: {}", idNode, e.getMessage());
            return null;
        }
    }

    // ---- Fleet-retrieval call -----------------------------------------

    private Result callFleet(String text, SummaryStyle style, Map<String, Object> meta) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);
        body.put("style", style.name());
        long t0 = System.currentTimeMillis();
        try {
            JsonNode resp = fleet.post().uri("/api/retrieval/summarize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(180));
            long ms = System.currentTimeMillis() - t0;
            String summary = resp == null ? "" : resp.path("summary").asText("");
            Map<String, Object> out = new LinkedHashMap<>(meta);
            out.put("style", style.name());
            out.put("elapsedMs", ms);
            out.put("summary", summary.trim());
            return Result.ok(out);
        } catch (Exception e) {
            log.warn("summarize via fleet-retrieval failed: {}", e.getMessage());
            return Result.error("summarize failed: " + e.getMessage());
        }
    }

    public record Result(boolean ok, Map<String, Object> payload, String error) {
        public static Result ok(Map<String, Object> p) { return new Result(true, p, null); }
        public static Result notFound(String msg)      { return new Result(false, Map.of(), msg); }
        public static Result error(String msg)         { return new Result(false, Map.of(), msg); }
        public Map<String, Object> asResponse() {
            Map<String, Object> m = new HashMap<>();
            if (ok) m.putAll(payload); else { m.put("error", error); m.put("ok", false); }
            return m;
        }
    }
}
