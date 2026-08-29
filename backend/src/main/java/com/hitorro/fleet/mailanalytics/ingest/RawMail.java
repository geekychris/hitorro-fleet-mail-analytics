/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import java.time.Instant;

/**
 * Vendor-neutral mail record produced by {@link MailSource} implementations
 * and serialized to NDJSON for the mesh enrichment pipeline. Fields are
 * kept flat to match what {@code mail-register.yaml} produces today; the
 * jvs-enrich step reshapes into the mail_email JVS type.
 *
 * <p>{@code sourceCursor} carries whatever the source uses to advance
 * its watermark (SQLite row id, IMAP UID). The orchestrator advances
 * the watermark from the max cursor it saw in the batch.</p>
 */
public record RawMail(
        String sourceId,
        long sourceCursor,
        String messageId,
        Instant dateReceived,
        String senderAddress,
        String senderName,
        String senderDomain,
        String subject,
        String bodyPreview,
        String mailboxUrl,
        boolean read,
        boolean flagged,
        long sizeBytes,
        int recipientCount,
        boolean newsletter
) {}
