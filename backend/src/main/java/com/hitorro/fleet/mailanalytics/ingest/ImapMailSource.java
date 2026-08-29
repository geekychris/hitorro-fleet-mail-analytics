/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import com.hitorro.fleet.mailanalytics.entities.Watermark;
import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.SentDateTerm;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Fetches mail from an IMAP server. Watermark is UIDVALIDITY + UIDNEXT
 * per folder — if UIDVALIDITY changes, the whole folder is re-crawled.
 * Uses the Jakarta Mail API + Angus provider (bundled at runtime).
 *
 * <p>Config keys: {@code host, port, user, password, ssl, folder}.</p>
 */
public class ImapMailSource implements MailSource {

    private static final Logger log = LoggerFactory.getLogger(ImapMailSource.class);

    private final String id;
    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final boolean ssl;
    private final String folder;

    public ImapMailSource(String id, Map<String, String> config) {
        this.id = id;
        this.host = config.getOrDefault("host", "");
        this.port = Integer.parseInt(config.getOrDefault("port", "993"));
        this.user = config.getOrDefault("user", "");
        this.password = config.getOrDefault("password", "");
        this.ssl = Boolean.parseBoolean(config.getOrDefault("ssl", "true"));
        this.folder = config.getOrDefault("folder", "INBOX");
    }

    @Override public String id() { return id; }
    @Override public String kind() { return "imap"; }

    @Override
    public Stream<RawMail> fetchSince(Watermark watermark, int batchSize) {
        Properties props = new Properties();
        props.put("mail.store.protocol", ssl ? "imaps" : "imap");
        props.put("mail.imap.host", host);
        props.put("mail.imap.port", String.valueOf(port));
        props.put("mail.imap.ssl.enable", String.valueOf(ssl));
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", String.valueOf(port));

        Session session = Session.getInstance(props);
        List<RawMail> out = new ArrayList<>(batchSize);
        try {
            Store store = session.getStore(ssl ? "imaps" : "imap");
            store.connect(host, port, user, password);
            try (IMAPFolder f = (IMAPFolder) store.getFolder(folder)) {
                f.open(Folder.READ_ONLY);
                long uidValidity = f.getUIDValidity();
                long sinceUid = 1L;
                if (watermark.getLastUidValidity() != null
                        && watermark.getLastUidValidity() == uidValidity
                        && watermark.getLastUid() != null) {
                    sinceUid = watermark.getLastUid() + 1;
                }
                Message[] msgs = f.getMessagesByUID(sinceUid, UIDMax(f));
                int taken = 0;
                for (Message m : msgs) {
                    if (taken >= batchSize) break;
                    if (!(m instanceof MimeMessage mime)) continue;
                    long uid = f.getUID(mime);
                    out.add(mapMessage(mime, uid, uidValidity));
                    taken++;
                }
            }
            store.close();
        } catch (Exception e) {
            log.warn("ImapMailSource[{}] fetchSince failed: {}", id, e.getMessage());
            throw new SqliteMailSource.IngestException("imap fetch failed for " + id, e);
        }
        return out.stream();
    }

    /** Angus API uses {@code long} for UID; there's no explicit "max UID"
     *  method — pass {@link Long#MAX_VALUE} as the upper bound and let
     *  the server clamp. */
    private static long UIDMax(IMAPFolder f) { return Long.MAX_VALUE; }

    private RawMail mapMessage(MimeMessage m, long uid, long uidValidity) throws Exception {
        Address[] fromArr = m.getFrom();
        String senderAddress = "";
        String senderName = "";
        if (fromArr != null && fromArr.length > 0 && fromArr[0] instanceof InternetAddress a) {
            senderAddress = a.getAddress() == null ? "" : a.getAddress();
            senderName = a.getPersonal() == null ? "" : a.getPersonal();
        }
        String senderDomain = "";
        int at = senderAddress.lastIndexOf('@');
        if (at >= 0) senderDomain = senderAddress.substring(at + 1).toLowerCase();
        String subject = m.getSubject() == null ? "" : m.getSubject();
        String bodyPreview = bodyPreview(m);
        Date sent = m.getSentDate() != null ? m.getSentDate() : m.getReceivedDate();
        Instant received = sent != null ? sent.toInstant() : Instant.now();
        boolean read = m.getFlags().contains(Flags.Flag.SEEN);
        boolean flagged = m.getFlags().contains(Flags.Flag.FLAGGED);
        int recipients = safeRecipientCount(m);
        long size = Math.max(0, m.getSize());
        String messageId = "imap:" + uidValidity + ":" + uid;
        boolean newsletter = m.getHeader("List-Unsubscribe") != null;
        return new RawMail(id, uid, messageId, received,
                senderAddress, senderName, senderDomain,
                subject, bodyPreview, folder, read, flagged, size, recipients, newsletter);
    }

    private static int safeRecipientCount(MimeMessage m) {
        try {
            Address[] to = m.getRecipients(Message.RecipientType.TO);
            Address[] cc = m.getRecipients(Message.RecipientType.CC);
            Address[] bcc = m.getRecipients(Message.RecipientType.BCC);
            return (to == null ? 0 : to.length)
                 + (cc == null ? 0 : cc.length)
                 + (bcc == null ? 0 : bcc.length);
        } catch (Exception e) { return 0; }
    }

    /** Cheap preview — first ~500 chars of the first text/plain part.
     *  Full-body extraction lives in the enrichment pipeline, not here. */
    private static String bodyPreview(MimeMessage m) {
        try {
            Object content = m.getContent();
            String s;
            if (content instanceof String str) s = str;
            else s = content == null ? "" : content.toString();
            return s.length() > 500 ? s.substring(0, 500) : s;
        } catch (Exception e) { return ""; }
    }

    @Override
    public Health health() {
        if (host == null || host.isEmpty()) return new Health(false, "no host configured");
        return new Health(true, "imap" + (ssl ? "s" : "") + "://" + user + "@" + host + ":" + port + "/" + folder);
    }

    /** Suppress unused-import warning noise; ComparisonTerm/SentDateTerm reserved
     *  for the backfill path that walks by date range instead of UID. */
    @SuppressWarnings("unused")
    private static final Class<?>[] IMAP_KEEP = { ComparisonTerm.class, SentDateTerm.class };
}
