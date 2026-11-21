# Operator guide

## Bringing up standalone (laptop)

Prereq: `hitorro-fleet-retrieval` on `:8095` — either its standalone mode
or shared mode reading the mesh pipeline's `${HT_HOME}/pipelines/lucene`.

```bash
# 1. build
cd hitorro-fleet-mail-analytics
mvn -pl backend -am clean install                # full (backend + React)
mvn -pl backend -am clean install -Dskip.frontend=true   # backend only

# 2. run
export HT_HOME=~/hitorro
java -jar backend/target/hitorro-fleet-mail-analytics-3.0.2.jar
```

App on `http://localhost:8100`. H2 file DB at `~/hthome/mailanalytics/db/`.

## Bringing up clustered (K8s / Orion)

```bash
# 1. build image
cd hitorro-fleet-mail-analytics
docker build -f deploy/Dockerfile -t hitorro/mail-analytics:3.0.1 .

# 2. supply Postgres + secrets + apply
kubectl apply -f deploy/k8s.yaml
```

## Ingest lifecycle

The pipeline stays the enrichment engine of record. Analytics is the conductor:

1. **backfill** — one-shot walk back to a horizon:
   ```
   curl -X POST 'http://localhost:8100/api/ingest/sources/mac-mail/backfill?daysBack=90'
   ```
2. **watch progress** — `GET /api/ingest/sources/mac-mail/status`
3. **delta** — the cron in `application-*.yml` runs a delta pull every 5 min

Batches land at `${mailanalytics.pipelines.hot-dir}/{source}/inbox/*.ndjson`.

By default the `logging` pipeline trigger only logs — you run the mesh
enrichment out-of-band:

```bash
mesh-pipeline.sh run \
  hitorro-fleet-mail-analytics/backend/src/main/resources/jobs/mail-enrich-from-ndjson.yaml \
  --wait --events
```

For hands-off operation, flip to the HTTP trigger by setting
`mailanalytics.pipeline.trigger=http` and pointing
`mailanalytics.pipeline.driver-url` at a running mesh-driver. The trigger
will POST substituted YAML to `/mesh/jobs/run` per batch.

## Pulling Mac mail from a mailbridge (`kind: bridge`)

When the app doesn't run on the Mac, which is always the case in Kubernetes, it can pull Mail
metadata from a **mailbridge** running on the Mac
([apple_mail_dump `bridge/`](https://github.com/geekychris/apple_mail_dump/tree/main/bridge)).
The bridge is a read-only HTTPS API over the Envelope Index. `BridgeMailSource` pins the
bridge's self-signed certificate by its SHA-256 fingerprint and sends the bridge's bearer
token. The cursor is the Envelope Index ROWID, kept in the watermark's `last_row_id`.

```yaml
mailanalytics:
  ingest:
    sources:
      - id: m1-mail
        kind: bridge
        cron: "0 */5 * * * *"
        config:
          base-url: https://192.168.1.70:18443
          token-file: /etc/mailbridge/token              # or token: ...
          fingerprint-file: /etc/mailbridge/fingerprint  # or fingerprint: AB:CD:...
          max-age-days: "7"                              # only mail from the last 7 days (0 = all)
```

Config keys are matched ignoring case, dashes and underscores, so they can also come from
environment variables, e.g. `MAILANALYTICS_INGEST_SOURCES_1_CONFIG_BASEURL`. A source with a
missing token or fingerprint is logged and skipped; the app still starts.
`GET /api/ingest/sources/{id}/status` shows the bridge's message count. The errors name the
cause:

| Error | Cause |
|---|---|
| 401 | wrong token |
| 403 | this cluster's source address is not in the bridge's `--allow` list |
| 503 | the bridge binary lacks Full Disk Access on the Mac |
| "unreachable" | network failure, or a certificate that doesn't match the pin |

## Without the mesh: index batches straight into fleet-retrieval

`mailanalytics.pipeline.trigger=retrieval` (`RetrievalIndexPipelineTrigger`) skips the mesh
enrichment job. It posts each ingest batch to fleet-retrieval's standalone ingest API
(`POST /api/ingest/indexes/{retrieval.default-index}/documents`), creating the index on first
use. The documents are typed `mail_email_flat` (`backend/src/main/resources/types/mail_email_flat.json`).
That type has no NLP (mls) fields, so fleet-retrieval needs no OpenNLP models. The type file
must be on fleet-retrieval's type path, `$HT_BIN/config/types/` (for example a ConfigMap mounted
there with `subPath`), and fleet-retrieval must be restarted to load it.

| | |
|---|---|
| **Works** | message counts and the date histogram (`date_received.date_s` holds epoch millis); top senders and domains, read/flagged/newsletter facets; message lists |
| **Empty or degraded** | entities (`top-entities`, NER-based topics and clusters); `title`/`body` are stored but not enriched; free-text search over subject and preview (they are indexed as plain text fields) |

`retrieval-type` (default `mail_email_flat`) and `retrieval-chunk` (documents per request,
default 200) live under `mailanalytics.pipeline`.

## Adding an alert

1. `POST /api/saved-queries` with the query DSL:
   ```json
   {"name":"unread from acme",
    "dslJson":"{\"index\":\"mail\",\"filters\":{\"sender_domain\":\"acme.com\",\"read\":\"false\"},\"limit\":50}"}
   ```
2. `POST /api/alerts`:
   ```json
   {"name":"acme mail","savedQueryId":1,"cron":"0 */10 * * * *",
    "deltaMode":"ANY_NEW","enabled":true,
    "deliveryChannelsJson":"[{\"channel\":\"INBOX\"},{\"channel\":\"EMAIL\",\"email\":\"you@x.com\"}]"}
   ```
3. Watch it in the UI's Inbox + Alerts pages, or `GET /api/alerts/{id}/firings`.

## Common paths

| endpoint                              | purpose                          |
|---------------------------------------|----------------------------------|
| `GET  /api/health`                    | mode + retrieval target + sources |
| `GET  /actuator/health`               | Spring Boot health                |
| `GET  /api/dashboard/overview`        | main dashboard payload            |
| `GET  /api/search/mail?q=...`         | free-text search                  |
| `POST /api/ingest/sources/{id}/run`   | one-shot delta pull               |
| `POST /api/alerts/{id}/run-now`       | force-evaluate an alert           |
| `POST /api/suggestions/run-now`       | re-scan for enrichment ideas      |
