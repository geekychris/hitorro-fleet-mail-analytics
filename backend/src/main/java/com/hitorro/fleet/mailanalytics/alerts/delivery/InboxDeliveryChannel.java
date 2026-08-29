/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.alerts.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.entities.AlertFiring;
import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import com.hitorro.fleet.mailanalytics.entities.DeliveryChannelKind;
import com.hitorro.fleet.mailanalytics.entities.InboxItem;
import com.hitorro.fleet.mailanalytics.entities.Severity;
import com.hitorro.fleet.mailanalytics.repo.InboxItemRepository;
import org.springframework.stereotype.Component;

@Component
public class InboxDeliveryChannel implements DeliveryChannel {

    private final InboxItemRepository repo;

    public InboxDeliveryChannel(InboxItemRepository repo) { this.repo = repo; }

    @Override public DeliveryChannelKind kind() { return DeliveryChannelKind.INBOX; }

    @Override
    public String deliver(AlertRule rule, AlertFiring firing, JsonNode target) {
        InboxItem item = new InboxItem();
        item.setFiringId(firing.getId());
        item.setTitle(rule.getName());
        item.setBodyPreview(firing.getResultSummaryJson() == null
                ? "alert fired"
                : truncate(firing.getResultSummaryJson(), 500));
        String sev = target.path("severity").asText("INFO");
        try { item.setSeverity(Severity.valueOf(sev)); } catch (Exception ignore) { item.setSeverity(Severity.INFO); }
        item.setPayloadJson(firing.getResultSummaryJson());
        InboxItem saved = repo.save(item);
        return "inbox item " + saved.getId();
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n));
    }
}
