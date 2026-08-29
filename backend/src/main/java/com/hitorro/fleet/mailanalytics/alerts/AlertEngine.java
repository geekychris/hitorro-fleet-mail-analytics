/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.alerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.entities.AlertDeltaMode;
import com.hitorro.fleet.mailanalytics.entities.AlertFiring;
import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import com.hitorro.fleet.mailanalytics.query.SavedQueryService;
import com.hitorro.fleet.mailanalytics.repo.AlertFiringRepository;
import com.hitorro.fleet.mailanalytics.repo.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class AlertEngine {

    private static final Logger log = LoggerFactory.getLogger(AlertEngine.class);

    private final AlertRuleRepository rules;
    private final AlertFiringRepository firings;
    private final SavedQueryService savedQueries;
    private final AlertDispatcher dispatcher;
    private final ObjectMapper mapper = new ObjectMapper();

    public AlertEngine(AlertRuleRepository rules, AlertFiringRepository firings,
                       SavedQueryService savedQueries, AlertDispatcher dispatcher) {
        this.rules = rules;
        this.firings = firings;
        this.savedQueries = savedQueries;
        this.dispatcher = dispatcher;
    }

    /** Runs one evaluation cycle for a rule. Persists a firing + delivers if the delta says fire. */
    @Transactional
    public Result evaluate(Long ruleId) {
        AlertRule rule = rules.findById(ruleId).orElseThrow();
        if (!rule.isEnabled()) return Result.disabled(ruleId);
        if (rule.getMutedUntil() != null && rule.getMutedUntil().isAfter(Instant.now())) {
            return Result.muted(ruleId, rule.getMutedUntil());
        }
        if (rule.getCooldownSeconds() > 0 && rule.getLastRunAt() != null
                && rule.getLastRunAt().plusSeconds(rule.getCooldownSeconds()).isAfter(Instant.now())) {
            return Result.cooldown(ruleId);
        }

        JsonNode resp;
        try { resp = savedQueries.run(rule.getSavedQueryId()); }
        catch (Exception e) {
            log.warn("alert[{}] query failed: {}", rule.getId(), e.getMessage());
            rule.setLastRunAt(Instant.now());
            rules.save(rule);
            return Result.error(ruleId, e.getMessage());
        }

        long total = resp == null ? 0 : resp.path("totalHits").asLong();
        List<String> ids = extractIds(resp);
        String fp = fingerprint(ids, total);

        boolean shouldFire = decide(rule, total, fp);
        rule.setLastRunAt(Instant.now());
        if (!shouldFire) {
            rules.save(rule);
            return Result.noFire(ruleId, total, fp);
        }

        AlertFiring firing = new AlertFiring();
        firing.setAlertRuleId(ruleId);
        firing.setFiredAt(Instant.now());
        firing.setFingerprint(fp);
        try {
            firing.setResultSummaryJson(mapper.writeValueAsString(
                    java.util.Map.of("totalHits", total,
                            "matchedIdsCount", ids.size(),
                            "query", resp == null ? "" : resp.path("query").asText(""))));
            firing.setMatchedDocIdsJson(mapper.writeValueAsString(ids));
        } catch (Exception e) {
            firing.setResultSummaryJson("{\"totalHits\":" + total + "}");
        }
        firings.save(firing);
        rule.setLastFingerprint(fp);
        rules.save(rule);
        dispatcher.dispatch(rule, firing);
        return Result.fired(ruleId, total, firing.getId());
    }

    private boolean decide(AlertRule rule, long total, String fp) {
        return switch (rule.getDeltaMode()) {
            case SCHEDULE_ONLY -> true;
            case ANY_NEW -> !fp.equals(rule.getLastFingerprint()) && total > 0;
            case COUNT_THRESHOLD -> total >= extractThreshold(rule, 1);
            case VALUE_THRESHOLD -> total >= extractThreshold(rule, 1);
        };
    }

    private long extractThreshold(AlertRule rule, long fallback) {
        try {
            JsonNode cfg = mapper.readTree(rule.getThresholdConfigJson() == null ? "{}" : rule.getThresholdConfigJson());
            return cfg.path("gte").asLong(fallback);
        } catch (Exception e) { return fallback; }
    }

    private List<String> extractIds(JsonNode resp) {
        List<String> out = new ArrayList<>();
        if (resp == null) return out;
        JsonNode docs = resp.path("documents");
        if (!docs.isArray()) return out;
        for (JsonNode d : docs) {
            JsonNode id = d.path("id");
            if (id.isObject()) out.add(id.path("id").asText());
            else out.add(id.asText());
        }
        return out;
    }

    private String fingerprint(List<String> ids, long total) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(String.valueOf(total).getBytes(StandardCharsets.UTF_8));
            for (String s : ids.stream().sorted().toList()) {
                md.update((byte) '|');
                md.update(s.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) { return "" + total; }
    }

    public record Result(Long ruleId, String status, Long total, String fingerprint, Long firingId, String detail) {
        static Result disabled(Long id) { return new Result(id, "disabled", null, null, null, null); }
        static Result muted(Long id, Instant until) { return new Result(id, "muted", null, null, null, "until " + until); }
        static Result cooldown(Long id) { return new Result(id, "cooldown", null, null, null, null); }
        static Result error(Long id, String msg) { return new Result(id, "error", null, null, null, msg); }
        static Result noFire(Long id, long total, String fp) { return new Result(id, "no-fire", total, fp, null, null); }
        static Result fired(Long id, long total, Long fid) { return new Result(id, "fired", total, null, fid, null); }
    }
}
