/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.alerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.alerts.delivery.DeliveryChannel;
import com.hitorro.fleet.mailanalytics.entities.AlertDelivery;
import com.hitorro.fleet.mailanalytics.entities.AlertFiring;
import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import com.hitorro.fleet.mailanalytics.entities.DeliveryChannelKind;
import com.hitorro.fleet.mailanalytics.entities.DeliveryStatus;
import com.hitorro.fleet.mailanalytics.repo.AlertDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fans a firing out to every channel listed in the rule's
 *  {@code deliveryChannelsJson}. One delivery record per (channel, target)
 *  tuple. In-memory retry with backoff; on final give-up, delivery is
 *  marked GIVEN_UP and left for the operator. */
@Component
public class AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);
    private static final int MAX_ATTEMPTS = 3;

    private final AlertDeliveryRepository deliveries;
    private final Map<DeliveryChannelKind, DeliveryChannel> channels = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public AlertDispatcher(AlertDeliveryRepository deliveries, List<DeliveryChannel> impls) {
        this.deliveries = deliveries;
        impls.forEach(c -> channels.put(c.kind(), c));
    }

    public void dispatch(AlertRule rule, AlertFiring firing) {
        JsonNode config;
        try { config = mapper.readTree(rule.getDeliveryChannelsJson()); }
        catch (Exception e) {
            log.warn("dispatch[{}] deliveryChannelsJson invalid: {}", rule.getId(), e.getMessage());
            return;
        }
        if (!config.isArray()) {
            log.warn("dispatch[{}] deliveryChannelsJson must be an array", rule.getId());
            return;
        }
        for (JsonNode target : config) {
            deliverOne(rule, firing, target);
        }
    }

    private void deliverOne(AlertRule rule, AlertFiring firing, JsonNode target) {
        String kindStr = target.path("channel").asText();
        DeliveryChannelKind kind;
        try { kind = DeliveryChannelKind.valueOf(kindStr.toUpperCase()); }
        catch (Exception e) {
            log.warn("dispatch[{}] unknown channel '{}'", rule.getId(), kindStr);
            return;
        }
        DeliveryChannel channel = channels.get(kind);
        if (channel == null) return;

        AlertDelivery d = new AlertDelivery();
        d.setFiringId(firing.getId());
        d.setChannel(kind);
        d.setTarget(target.path(kind == DeliveryChannelKind.EMAIL ? "email"
                       : kind == DeliveryChannelKind.WEBHOOK ? "webhook" : "user").asText());
        d.setStatus(DeliveryStatus.PENDING);
        AlertDelivery saved = deliveries.save(d);

        int attempt = 0;
        while (attempt < MAX_ATTEMPTS) {
            attempt++;
            saved.setAttempts(attempt);
            try {
                String detail = channel.deliver(rule, firing, target);
                saved.setStatus(DeliveryStatus.SENT);
                saved.setDeliveredAt(Instant.now());
                saved.setLastError(null);
                deliveries.save(saved);
                log.info("delivery[{}] {} rule={}: {}", kind, saved.getId(), rule.getId(), detail);
                return;
            } catch (Exception e) {
                saved.setLastError(e.getMessage());
                saved.setStatus(attempt < MAX_ATTEMPTS ? DeliveryStatus.RETRYING : DeliveryStatus.GIVEN_UP);
                deliveries.save(saved);
                log.warn("delivery[{}] rule={} attempt {}/{} failed: {}", kind, rule.getId(), attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                }
            }
        }
    }
}
