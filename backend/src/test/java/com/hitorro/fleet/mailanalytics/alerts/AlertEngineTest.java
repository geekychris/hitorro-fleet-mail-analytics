/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.alerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.alerts.delivery.DeliveryChannel;
import com.hitorro.fleet.mailanalytics.entities.AlertDeltaMode;
import com.hitorro.fleet.mailanalytics.entities.AlertFiring;
import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import com.hitorro.fleet.mailanalytics.entities.DeliveryChannelKind;
import com.hitorro.fleet.mailanalytics.query.SavedQueryService;
import com.hitorro.fleet.mailanalytics.repo.AlertDeliveryRepository;
import com.hitorro.fleet.mailanalytics.repo.AlertFiringRepository;
import com.hitorro.fleet.mailanalytics.repo.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertEngineTest {

    private AlertRuleRepository rules;
    private AlertFiringRepository firings;
    private SavedQueryService savedQueries;
    private DeliveryChannel inboxChannel;
    private AlertDispatcher dispatcher;
    private AlertDeliveryRepository deliveries;
    private ObjectMapper mapper;
    private AtomicLong firingIds;
    private AtomicReference<AlertFiring> lastSavedFiring;

    @BeforeEach
    void setup() {
        rules = mock(AlertRuleRepository.class);
        firings = mock(AlertFiringRepository.class);
        savedQueries = mock(SavedQueryService.class);
        inboxChannel = mock(DeliveryChannel.class);
        when(inboxChannel.kind()).thenReturn(DeliveryChannelKind.INBOX);
        deliveries = mock(AlertDeliveryRepository.class);
        when(deliveries.save(any())).thenAnswer(inv -> {
            var d = (com.hitorro.fleet.mailanalytics.entities.AlertDelivery) inv.getArgument(0);
            if (d.getId() == null) d.setId(1L);
            return d;
        });
        dispatcher = new AlertDispatcher(deliveries, List.of(inboxChannel));
        mapper = new ObjectMapper();
        firingIds = new AtomicLong();
        lastSavedFiring = new AtomicReference<>();
        when(firings.save(any(AlertFiring.class))).thenAnswer(inv -> {
            AlertFiring f = inv.getArgument(0);
            if (f.getId() == null) f.setId(firingIds.incrementAndGet());
            lastSavedFiring.set(f);
            return f;
        });
    }

    @Test
    void any_new_fires_on_first_run_when_hits_present_and_delivers_to_channels() throws Exception {
        AlertRule rule = enabledRule(AlertDeltaMode.ANY_NEW, null);
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(savedQueries.run(any())).thenReturn(execResult(3L, List.of("doc1", "doc2", "doc3")));

        AlertEngine.Result r = new AlertEngine(rules, firings, savedQueries, dispatcher).evaluate(1L);

        assertThat(r.status()).isEqualTo("fired");
        verify(inboxChannel).deliver(any(), any(), any());
        assertThat(lastSavedFiring.get().getFingerprint()).isNotBlank();
    }

    @Test
    void any_new_does_not_refire_when_fingerprint_unchanged() throws Exception {
        AlertRule rule = enabledRule(AlertDeltaMode.ANY_NEW, null);
        AlertEngine engine = new AlertEngine(rules, firings, savedQueries, dispatcher);
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(savedQueries.run(any())).thenReturn(execResult(2L, List.of("a", "b")));

        AlertEngine.Result first = engine.evaluate(1L);
        assertThat(first.status()).isEqualTo("fired");

        // Second run: same fingerprint → no-fire, no additional delivery.
        AlertEngine.Result second = engine.evaluate(1L);
        assertThat(second.status()).isEqualTo("no-fire");
        verify(inboxChannel, org.mockito.Mockito.times(1)).deliver(any(), any(), any());
    }

    @Test
    void muted_rule_short_circuits() {
        AlertRule rule = enabledRule(AlertDeltaMode.ANY_NEW, null);
        rule.setMutedUntil(Instant.now().plusSeconds(3600));
        when(rules.findById(1L)).thenReturn(Optional.of(rule));

        AlertEngine.Result r = new AlertEngine(rules, firings, savedQueries, dispatcher).evaluate(1L);

        assertThat(r.status()).isEqualTo("muted");
        verify(savedQueries, never()).run(any());
        verify(inboxChannel, never()).deliver(any(), any(), any());
    }

    @Test
    void count_threshold_fires_only_above_gte() throws Exception {
        AlertRule rule = enabledRule(AlertDeltaMode.COUNT_THRESHOLD, "{\"gte\":10}");
        when(rules.findById(1L)).thenReturn(Optional.of(rule));

        when(savedQueries.run(any())).thenReturn(execResult(5L, List.of()));
        assertThat(new AlertEngine(rules, firings, savedQueries, dispatcher).evaluate(1L).status()).isEqualTo("no-fire");

        when(savedQueries.run(any())).thenReturn(execResult(10L, List.of("x")));
        assertThat(new AlertEngine(rules, firings, savedQueries, dispatcher).evaluate(1L).status()).isEqualTo("fired");
    }

    private AlertRule enabledRule(AlertDeltaMode mode, String thresholdJson) {
        AlertRule r = new AlertRule();
        r.setId(1L);
        r.setName("test");
        r.setCron("0 * * * * *");
        r.setEnabled(true);
        r.setSavedQueryId(99L);
        r.setDeltaMode(mode);
        r.setThresholdConfigJson(thresholdJson);
        r.setDeliveryChannelsJson("[{\"channel\":\"INBOX\"}]");
        return r;
    }

    private JsonNode execResult(long total, List<String> ids) throws Exception {
        StringBuilder docs = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) docs.append(",");
            docs.append("{\"id\":{\"id\":\"").append(ids.get(i)).append("\"}}");
        }
        docs.append("]");
        return mapper.readTree("{\"totalHits\":" + total + ",\"documents\":" + docs + "}");
    }
}
