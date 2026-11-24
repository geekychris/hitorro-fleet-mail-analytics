/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.alerts.delivery;

import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailDeliveryChannelTest {

    @Test
    void subjectIsOneLine() {
        AlertRule r = new AlertRule();
        r.setName("Invoices\r\nBcc: someone@example.com");
        assertEquals("[mail-analytics] Invoices  Bcc: someone@example.com", EmailDeliveryChannel.subject(r));
    }

    @Test
    void subjectWithoutName() {
        assertEquals("[mail-analytics] ", EmailDeliveryChannel.subject(new AlertRule()));
    }
}
