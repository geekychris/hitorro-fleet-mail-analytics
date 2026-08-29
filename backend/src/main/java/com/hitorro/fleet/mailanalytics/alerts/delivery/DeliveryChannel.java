/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.alerts.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.entities.AlertFiring;
import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import com.hitorro.fleet.mailanalytics.entities.DeliveryChannelKind;

/** One outbound channel — email, webhook, in-app inbox. Impl decides
 *  how to render the payload and where to send it. */
public interface DeliveryChannel {

    DeliveryChannelKind kind();

    /** Deliver the firing. Throws on transport failure so the dispatcher
     *  can retry / mark {@code FAILED}. Return string is a human detail
     *  captured in the AlertDelivery record. */
    String deliver(AlertRule rule, AlertFiring firing, JsonNode target);
}
