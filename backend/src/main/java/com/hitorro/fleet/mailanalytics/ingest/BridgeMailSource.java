/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.fleet.mailanalytics.ingest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hitorro.fleet.mailanalytics.config.JsonValues;
import com.hitorro.fleet.mailanalytics.entities.Watermark;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Pulls Apple Mail metadata from a <em>mailbridge</em> running on the Mac
 * (geekychris/apple_mail_dump, {@code bridge/}): a read-only HTTPS API over
 * Mail's Envelope Index. The data stays on the Mac; this source pulls deltas.
 *
 * <p>Security: the bridge uses a self-signed certificate, so instead of a CA
 * this source <b>pins the certificate's SHA-256 fingerprint</b> and refuses any
 * other certificate. Requests carry the bridge's bearer token.</p>
 *
 * <p>The cursor is the Envelope Index ROWID ({@code since} / {@code next_since}),
 * stored in {@link Watermark#getLastRowId()} like {@link SqliteMailSource}.</p>
 *
 * <p>Config ({@code mailanalytics.ingest.sources[].config}; keys are matched
 * ignoring case, dashes and underscores, so environment-variable binding works):</p>
 * <ul>
 *   <li>{@code base-url}: e.g. {@code https://192.168.1.70:18443} (required)</li>
 *   <li>{@code token} or {@code token-file}: the bearer token (required)</li>
 *   <li>{@code fingerprint} or {@code fingerprint-file}: SHA-256 of the
 *       bridge certificate, hex with or without colons (required)</li>
 *   <li>{@code max-age-days}: only mail received in the last N days (0 = all;
 *       default 0). Bounds the first backfill.</li>
 *   <li>{@code timeout-ms}: per-request timeout (default 30000)</li>
 * </ul>
 */
public class BridgeMailSource implements MailSource {

    private static final Logger log = LoggerFactory.getLogger(BridgeMailSource.class);
    private static final int MAX_PAGE = 1000;

    private final String id;
    private final URI baseUrl;
    private final String token;
    private final String pinnedFingerprint;
    private final int maxAgeDays;
    private final Duration timeout;
    private final HttpClient http;
    private final Clock clock;
    private final ObjectMapper mapper = com.hitorro.fleet.mailanalytics.config.Jackson2Mappers.json();

    public BridgeMailSource(String id, Map<String, String> config) {
        this(id, config, Clock.systemUTC());
    }

    BridgeMailSource(String id, Map<String, String> config, Clock clock) {
        this.id = id;
        this.clock = clock;
        String url = required(config, "base-url");
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("bridge source '" + id + "': base-url must be https://");
        }
        this.baseUrl = URI.create(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
        this.token = secret(config, "token");
        this.pinnedFingerprint = normalizeFingerprint(secret(config, "fingerprint"));
        if (pinnedFingerprint.length() != 64) {
            throw new IllegalArgumentException("bridge source '" + id + "': fingerprint must be a SHA-256 (64 hex digits)");
        }
        this.maxAgeDays = Integer.parseInt(get(config, "max-age-days", "0"));
        this.timeout = Duration.ofMillis(Long.parseLong(get(config, "timeout-ms", "30000")));
        this.http = HttpClient.newBuilder()
                .sslContext(pinnedContext(pinnedFingerprint))
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override public String id() { return id; }
    @Override public String kind() { return "bridge"; }

    @Override
    public Stream<RawMail> fetchSince(Watermark watermark, int batchSize) {
        long since = watermark.getLastRowId() == null ? 0L : watermark.getLastRowId();
        int limit = Math.max(1, Math.min(batchSize, MAX_PAGE));
        StringBuilder q = new StringBuilder("/v1/mail/messages?since=").append(since).append("&limit=").append(limit);
        if (maxAgeDays > 0) {
            long after = clock.instant().minus(Duration.ofDays(maxAgeDays)).getEpochSecond();
            q.append("&received_after=").append(after);
        }
        JsonNode body = getJson(q.toString());
        List<RawMail> out = new ArrayList<>();
        for (JsonNode m : body.path("messages")) {
            out.add(map(m));
        }
        return out.stream();
    }

    @Override
    public Health health() {
        try {
            JsonNode st = getJson("/v1/status");
            JsonNode mail = st.path("mail");
            if (!JsonValues.asBoolean(mail.path("ok"), false)) {
                return new Health(false, "bridge reachable; mail unavailable: " + JsonValues.asText(mail.path("error"), "unknown"));
            }
            JsonNode c = mail.path("counts");
            return new Health(true, JsonValues.asLong(c.path("messages")) + " messages, max id " + JsonValues.asLong(c.path("max_id"))
                    + " @ " + baseUrl.getAuthority());
        } catch (RuntimeException e) {
            return new Health(false, e.getMessage());
        }
    }

    RawMail map(JsonNode m) {
        long rowId = JsonValues.asLong(m.path("id"));
        JsonNode from = m.path("from");
        String senderAddress = JsonValues.asText(from.path("address"), "");
        String messageId = firstNonBlank(JsonValues.asText(m.path("message_id"), ""), JsonValues.asText(m.path("remote_id"), ""));
        if (messageId.isEmpty()) messageId = "macmail:" + rowId;
        String recv = JsonValues.asText(m.path("date_received"), "");
        Instant received = recv.isEmpty() ? Instant.EPOCH : Instant.parse(recv);
        JsonNode flags = m.path("flags");
        int recipients = m.path("to").size() + m.path("cc").size();
        return new RawMail(id, rowId, messageId, received,
                senderAddress, JsonValues.asText(from.path("name"), ""), domainOf(senderAddress),
                JsonValues.asText(m.path("subject"), ""), JsonValues.asText(m.path("snippet"), ""), JsonValues.asText(m.path("mailbox"), ""),
                JsonValues.asBoolean(flags.path("read"), false), JsonValues.asBoolean(flags.path("flagged"), false),
                JsonValues.asLong(m.path("size"), 0), recipients, false);
    }

    private JsonNode getJson(String pathAndQuery) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .timeout(timeout)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SqliteMailSource.IngestException("bridge " + baseUrl.getAuthority() + " unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SqliteMailSource.IngestException("interrupted calling bridge", e);
        }
        int code = resp.statusCode();
        if (code != 200) {
            String kind = "";
            try { kind = JsonValues.asText(mapper.readTree(resp.body()).path("error"), ""); } catch (tools.jackson.core.JacksonException ignore) { }
            String detail = switch (code) {
                case 401 -> "bridge rejected the token (401)";
                case 403 -> "bridge refused this source address (403): add it to the bridge's --allow list";
                case 429 -> "bridge rate limit (429)";
                case 503 -> "mail_not_set_up".equals(kind)
                        ? "Mail.app is not set up on the bridge's Mac (503 mail_not_set_up): point this source at the Mac where Mail is used"
                        : "bridge cannot read Mail (503 " + kind + "): grant Full Disk Access to the mailbridge binary";
                default -> "bridge returned HTTP " + code + (kind.isEmpty() ? "" : " (" + kind + ")");
            };
            log.warn("BridgeMailSource[{}] {} {}", id, pathAndQuery.replaceAll("\\?.*", ""), detail);
            throw new SqliteMailSource.IngestException(detail, null);
        }
        try {
            return mapper.readTree(resp.body());
        } catch (tools.jackson.core.JacksonException e) {
            throw new SqliteMailSource.IngestException("bridge returned invalid JSON", e);
        }
    }

    // ---- certificate pinning ----------------------------------------------

    static String normalizeFingerprint(String fp) {
        return fp == null ? "" : fp.replace(":", "").replace(" ", "").trim().toLowerCase(Locale.ROOT);
    }

    static String sha256Hex(X509Certificate cert) throws CertificateEncodingException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** An SSLContext that accepts exactly one server certificate, by SHA-256.
     *  Extends X509ExtendedTrustManager so JSSE does not add CA or hostname
     *  checks on top: the pin is the whole trust decision. */
    static SSLContext pinnedContext(String pinnedHex) {
        X509ExtendedTrustManager tm = new X509ExtendedTrustManager() {
            private void check(X509Certificate[] chain) throws CertificateException {
                if (chain == null || chain.length == 0) throw new CertificateException("no server certificate");
                String got = sha256Hex(chain[0]);
                if (!MessageDigest.isEqual(got.getBytes(StandardCharsets.US_ASCII), pinnedHex.getBytes(StandardCharsets.US_ASCII))) {
                    throw new CertificateException("bridge certificate fingerprint does not match the pinned one");
                }
                chain[0].checkValidity();
            }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { check(chain); }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket s) throws CertificateException { check(chain); }
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine e) throws CertificateException { check(chain); }
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { throw new CertificateException("client role unsupported"); }
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket s) throws CertificateException { throw new CertificateException("client role unsupported"); }
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine e) throws CertificateException { throw new CertificateException("client role unsupported"); }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{tm}, null);
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("cannot build pinned TLS context", e);
        }
    }

    // ---- config helpers ---------------------------------------------------

    private static String norm(String k) {
        return k.replace("-", "").replace("_", "").replace(".", "").toLowerCase(Locale.ROOT);
    }

    private static String get(Map<String, String> config, String key, String def) {
        String want = norm(key);
        for (var e : config.entrySet()) {
            if (norm(e.getKey()).equals(want) && e.getValue() != null && !e.getValue().isBlank()) {
                return e.getValue().trim();
            }
        }
        return def;
    }

    private String required(Map<String, String> config, String key) {
        String v = get(config, key, null);
        if (v == null) throw new IllegalArgumentException("bridge source '" + id + "': config '" + key + "' is required");
        return v;
    }

    /** {@code key} inline, or read from {@code key-file} (a mounted Secret). */
    private String secret(Map<String, String> config, String key) {
        String v = get(config, key, null);
        if (v != null) return v;
        String file = get(config, key + "-file", null);
        if (file == null) {
            throw new IllegalArgumentException("bridge source '" + id + "': config '" + key + "' or '" + key + "-file' is required");
        }
        try {
            return Files.readString(Path.of(file)).trim();
        } catch (IOException e) {
            throw new IllegalArgumentException("bridge source '" + id + "': cannot read " + key + "-file", e);
        }
    }

    private static String domainOf(String addr) {
        int at = addr.lastIndexOf('@');
        return at < 0 ? "" : addr.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }
}
