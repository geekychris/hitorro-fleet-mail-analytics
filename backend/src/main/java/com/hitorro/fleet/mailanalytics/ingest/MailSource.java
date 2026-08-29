/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.hitorro.fleet.mailanalytics.entities.Watermark;

import java.util.stream.Stream;

/**
 * Pluggable mail ingest source. Implementations pull deltas from wherever
 * mail lives (Mac Mail SQLite, IMAP, ...) and emit {@link RawMail}
 * batches sized to the caller's request.
 */
public interface MailSource {

    /** Stable id matching the {@code Watermark.source_id} column and
     *  {@code mailanalytics.ingest.sources[].id} config entry. */
    String id();

    /** Human string for the UI Settings page. */
    String kind();

    /**
     * Pull up to {@code batchSize} records more recent than the watermark.
     * Callers <em>must</em> close the stream — SQLite result sets and
     * IMAP folders both hold resources.
     */
    Stream<RawMail> fetchSince(Watermark watermark, int batchSize);

    /** Fast liveness probe for the /api/ingest/sources/{id}/status endpoint. */
    Health health();

    /** Snapshot of source state — surfaced in the UI. */
    record Health(boolean ok, String detail) {}
}
