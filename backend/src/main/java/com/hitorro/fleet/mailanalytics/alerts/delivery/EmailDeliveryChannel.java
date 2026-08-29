/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.alerts.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.entities.AlertFiring;
import com.hitorro.fleet.mailanalytics.entities.AlertRule;
import com.hitorro.fleet.mailanalytics.entities.DeliveryChannelKind;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Component
public class EmailDeliveryChannel implements DeliveryChannel {

    private final MailAnalyticsProperties props;

    public EmailDeliveryChannel(MailAnalyticsProperties props) { this.props = props; }

    @Override public DeliveryChannelKind kind() { return DeliveryChannelKind.EMAIL; }

    @Override
    public String deliver(AlertRule rule, AlertFiring firing, JsonNode target) {
        MailAnalyticsProperties.Delivery.Email cfg = props.getDelivery().getEmail();
        if (!cfg.isEnabled()) return "email delivery disabled — skipped";
        String to = target.path("email").asText();
        if (to.isEmpty()) throw new IllegalArgumentException("email target missing 'email'");

        Properties p = new Properties();
        p.put("mail.smtp.host", cfg.getHost());
        p.put("mail.smtp.port", String.valueOf(cfg.getPort()));
        p.put("mail.smtp.starttls.enable", String.valueOf(cfg.isStarttls()));
        boolean auth = cfg.getUser() != null && !cfg.getUser().isEmpty();
        p.put("mail.smtp.auth", String.valueOf(auth));

        Session session = auth
                ? Session.getInstance(p, new jakarta.mail.Authenticator() {
                    @Override protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                        return new jakarta.mail.PasswordAuthentication(cfg.getUser(), cfg.getPassword());
                    }})
                : Session.getInstance(p);

        try {
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(cfg.getFrom()));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            msg.setSubject("[mail-analytics] " + rule.getName());
            msg.setText(bodyText(rule, firing));
            Transport.send(msg);
            return "sent to " + to + " via " + cfg.getHost() + ":" + cfg.getPort();
        } catch (Exception e) {
            throw new RuntimeException("smtp send failed: " + e.getMessage(), e);
        }
    }

    private String bodyText(AlertRule rule, AlertFiring firing) {
        return "Alert: " + rule.getName() + "\n"
             + "Fired at: " + firing.getFiredAt() + "\n"
             + "Rule id: " + rule.getId() + "\n\n"
             + "Summary:\n" + (firing.getResultSummaryJson() == null ? "" : firing.getResultSummaryJson()) + "\n\n"
             + "Matched doc ids: " + (firing.getMatchedDocIdsJson() == null ? "[]" : firing.getMatchedDocIdsJson()) + "\n";
    }
}
