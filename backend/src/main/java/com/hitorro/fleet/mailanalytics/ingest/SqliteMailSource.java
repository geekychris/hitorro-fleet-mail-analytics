/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.hitorro.fleet.mailanalytics.entities.Watermark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads Mac Mail's Envelope Index SQLite database.
 * ROWID is monotonic — used as the watermark cursor. The joined shape
 * matches what {@code mail-register.yaml} produces today so downstream
 * enrichment stays unchanged.
 *
 * <p>Read-only: opens the DB in immutable mode so Mail.app can hold the
 * write lock without blocking us.</p>
 */
public class SqliteMailSource implements MailSource {

    private static final Logger log = LoggerFactory.getLogger(SqliteMailSource.class);

    private static final String SELECT_SINCE = """
        SELECT m.ROWID, m.date_received, m.date_sent,
               addr.address, addr.comment AS sender_name,
               subj.subject,
               summ.summary,
               mb.url,
               m.read, m.flagged, m.size,
               m.remote_id, m.original_mailbox
        FROM messages m
        LEFT JOIN addresses  addr ON addr.ROWID = m.sender
        LEFT JOIN subjects   subj ON subj.ROWID = m.subject
        LEFT JOIN summaries  summ ON summ.ROWID = m.summary
        LEFT JOIN mailboxes  mb   ON mb.ROWID   = m.mailbox
        WHERE m.ROWID > ?
        ORDER BY m.ROWID ASC
        LIMIT ?
        """;

    private final String id;
    private final String dbPath;

    public SqliteMailSource(String id, String dbPath) {
        this.id = id;
        this.dbPath = dbPath;
    }

    @Override public String id() { return id; }
    @Override public String kind() { return "sqlite"; }

    @Override
    public Stream<RawMail> fetchSince(Watermark watermark, int batchSize) {
        long since = watermark.getLastRowId() == null ? 0L : watermark.getLastRowId();
        String url = "jdbc:sqlite:" + dbPath + "?mode=ro&immutable=1";
        try {
            Connection conn = DriverManager.getConnection(url);
            PreparedStatement ps = conn.prepareStatement(SELECT_SINCE);
            ps.setLong(1, since);
            ps.setInt(2, batchSize);
            ResultSet rs = ps.executeQuery();
            // Drain into a list — SQLite result sets don't survive
            // async processing well, and batchSize is bounded so this
            // is memory-safe. Then close everything.
            List<RawMail> out = new ArrayList<>(batchSize);
            while (rs.next()) out.add(map(rs));
            rs.close(); ps.close(); conn.close();
            return out.stream();
        } catch (SQLException e) {
            log.warn("SqliteMailSource[{}] fetchSince failed: {}", id, e.getMessage());
            throw new IngestException("sqlite fetch failed for " + id, e);
        }
    }

    private RawMail map(ResultSet rs) throws SQLException {
        long rowId = rs.getLong("ROWID");
        long dateReceivedEpoch = rs.getLong("date_received");
        // Mail.app stores dates as CFAbsoluteTime seconds since 2001-01-01.
        Instant received = Instant.ofEpochSecond(978_307_200L + dateReceivedEpoch);
        String senderAddress = rs.getString("address");
        String senderName = rs.getString("sender_name");
        String senderDomain = domainOf(senderAddress);
        String subject = rs.getString("subject");
        String summary = rs.getString("summary");
        String mailboxUrl = rs.getString("url");
        String remoteId = rs.getString("remote_id");
        boolean read = rs.getInt("read") != 0;
        boolean flagged = rs.getInt("flagged") != 0;
        long size = rs.getLong("size");
        String messageId = remoteId != null && !remoteId.isEmpty()
                ? remoteId : "macmail:" + rowId;
        return new RawMail(id, rowId, messageId, received,
                nullSafe(senderAddress), nullSafe(senderName), senderDomain,
                nullSafe(subject), nullSafe(summary), nullSafe(mailboxUrl),
                read, flagged, size, 0, false);
    }

    private static String domainOf(String addr) {
        if (addr == null) return "";
        int at = addr.lastIndexOf('@');
        return at < 0 ? "" : addr.substring(at + 1).toLowerCase();
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    @Override
    public Health health() {
        File f = new File(dbPath);
        if (!f.exists()) return new Health(false, "no envelope index at " + dbPath);
        if (!f.canRead()) return new Health(false, "envelope index not readable — grant Full Disk Access to your JVM");
        return new Health(true, f.length() + " bytes @ " + f.getAbsolutePath());
    }

    public static class IngestException extends RuntimeException {
        public IngestException(String m, Throwable c) { super(m, c); }
    }
}
