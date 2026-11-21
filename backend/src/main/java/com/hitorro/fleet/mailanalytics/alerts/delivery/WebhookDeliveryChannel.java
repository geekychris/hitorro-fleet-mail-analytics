/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.alerts.delivery;

import com.hitorro.fleet.mailanalytics.config.JsonCodecs;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.entities.AlertFiring;
import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import com.hitorro.fleet.mailanalytics.entities.DeliveryChannelKind;
import com.hitorro.fleet.mailanalytics.entities.WebhookConfig;
import com.hitorro.fleet.mailanalytics.repo.WebhookConfigRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import com.hitorro.fleet.mailanalytics.config.JsonValues;

@Component
public class WebhookDeliveryChannel implements DeliveryChannel {

    private final WebhookConfigRepository repo;
    private final MailAnalyticsProperties props;
    // ISO-8601 java.time values, as the REST API (report artifacts / webhook bodies; see Jackson2Mappers)
    private final ObjectMapper mapper = com.hitorro.fleet.mailanalytics.config.Jackson2Mappers.isoDates();

    private final JsonCodecs codecs;

    public WebhookDeliveryChannel(WebhookConfigRepository repo, MailAnalyticsProperties props, JsonCodecs codecs) {
        this.repo = repo;
        this.props = props;
        this.codecs = codecs;
    }

    @Override public DeliveryChannelKind kind() { return DeliveryChannelKind.WEBHOOK; }

    @Override
    public String deliver(AlertRule rule, AlertFiring firing, JsonNode target) {
        String name = JsonValues.asText(target.path("webhook"));
        if (name.isEmpty()) throw new IllegalArgumentException("webhook target missing 'webhook' name");
        WebhookConfig hook = repo.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("no webhook named " + name));
        if (!hook.isEnabled()) return "webhook '" + name + "' disabled — skipped";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rule", Map.of("id", rule.getId(), "name", rule.getName(), "cron", rule.getCron()));
        body.put("firing", Map.of("id", firing.getId(), "at", firing.getFiredAt(),
                                   "fingerprint", firing.getFingerprint()));
        body.put("resultSummary", asJsonOrString(firing.getResultSummaryJson()));
        body.put("matchedDocIds", asJsonOrString(firing.getMatchedDocIdsJson()));

        try {
            String bodyStr = mapper.writeValueAsString(body);
            WebClient.RequestBodySpec req = codecs.webClientBuilder()
                    .baseUrl(hook.getUrl()).build()
                    .post().uri("").header("Content-Type", "application/json");
            if (hook.getHeadersJson() != null && !hook.getHeadersJson().isBlank()) {
                JsonNode hdrs = mapper.readTree(hook.getHeadersJson());
                hdrs.properties().forEach(e -> req.header(e.getKey(), JsonValues.asText(e.getValue())));
            }
            if (hook.getSecret() != null && !hook.getSecret().isEmpty()) {
                req.header("X-Signature", "sha256=" + hmacSha256(bodyStr, hook.getSecret()));
            }
            req.bodyValue(bodyStr).retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(props.getDelivery().getWebhook().getTimeoutMs()));
            return "POST " + hook.getUrl() + " → 2xx";
        } catch (Exception e) {
            throw new RuntimeException("webhook '" + name + "' delivery failed: " + e.getMessage(), e);
        }
    }

    private Object asJsonOrString(String s) {
        if (s == null || s.isBlank()) return null;
        try { return mapper.readTree(s); } catch (Exception ignore) { return s; }
    }

    private static String hmacSha256(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
