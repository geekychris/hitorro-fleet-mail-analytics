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
java -jar backend/target/hitorro-fleet-mail-analytics-3.0.1.jar
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
