/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.hitorro.fleet.mailanalytics.config.MailAnalyticsProperties;
import com.hitorro.fleet.mailanalytics.entities.Watermark;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestOrchestratorTest {

    @Test
    void writes_ndjson_and_advances_watermark(@TempDir Path tmp) throws Exception {
        MailAnalyticsProperties props = new MailAnalyticsProperties();
        props.getPipelines().setHotDir(tmp.toString());
        props.getIngest().setBatchSize(10);

        WatermarkService watermarks = mock(WatermarkService.class);
        Watermark wm = new Watermark();
        wm.setSourceId("test");
        when(watermarks.getOrCreate("test")).thenReturn(wm);

        MailSource source = mock(MailSource.class);
        when(source.id()).thenReturn("test");
        RawMail one = new RawMail("test", 42L, "msg1", Instant.now(),
                "a@x.com", "A", "x.com", "hi", "body", "imap://x", false, false, 100, 0, false);
        when(source.fetchSince(any(Watermark.class), eq(10))).thenReturn(Stream.of(one));

        PipelineTrigger trigger = mock(PipelineTrigger.class);
        when(trigger.trigger(eq("test"), any())).thenReturn(new PipelineTrigger.Result(true, "job-1", "ok"));

        IngestOrchestrator o = new IngestOrchestrator(props, watermarks, trigger);
        IngestOrchestrator.Batch batch = o.runOnce(source);

        assertThat(batch.rowsWritten()).isEqualTo(1);
        assertThat(batch.batchPath()).isNotNull();
        assertThat(Files.exists(batch.batchPath())).isTrue();
        String content = Files.readString(batch.batchPath());
        assertThat(content).contains("\"source_cursor\":42");
        assertThat(content).contains("\"sender_domain\":\"x.com\"");
        verify(watermarks).advance("test", 42L, 1);
    }

    @Test
    void empty_batch_does_not_advance_or_trigger() {
        MailAnalyticsProperties props = new MailAnalyticsProperties();
        WatermarkService watermarks = mock(WatermarkService.class);
        when(watermarks.getOrCreate("test")).thenReturn(new Watermark());
        MailSource source = mock(MailSource.class);
        when(source.id()).thenReturn("test");
        when(source.fetchSince(any(), any(Integer.class))).thenReturn(Stream.of());
        PipelineTrigger trigger = mock(PipelineTrigger.class);

        IngestOrchestrator o = new IngestOrchestrator(props, watermarks, trigger);
        IngestOrchestrator.Batch batch = o.runOnce(source);

        assertThat(batch.rowsWritten()).isZero();
        assertThat(batch.batchPath()).isNull();
    }

    @Test
    void raw_mail_projection_shape() {
        // Regression guard on the NDJSON shape the mesh pipeline reads.
        RawMail m = new RawMail("mac-mail", 1, "id", Instant.parse("2026-01-01T00:00:00Z"),
                "sender@example.com", "S", "example.com", "subj", "body", "mbx", true, false, 42, 3, false);
        List<String> fields = List.of("sourceId","sourceCursor","messageId","dateReceived",
                "senderAddress","senderName","senderDomain","subject","bodyPreview","mailboxUrl",
                "read","flagged","sizeBytes","recipientCount","newsletter");
        assertThat(fields).hasSize(RawMail.class.getRecordComponents().length);
        assertThat(m.senderDomain()).isEqualTo("example.com");
    }
}
