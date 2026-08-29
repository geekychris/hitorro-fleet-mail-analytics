# hitorro-fleet-mail-analytics

Fleet member that turns the hitorro mail pipeline into an "all seeing eye" for
your inbox. Sits beside `hitorro-fleet-retrieval` and owns everything that
needs to survive a restart:

- **Ingest orchestration** — pluggable mail sources (Mac Mail SQLite or IMAP),
  per-source watermarks, backfill + delta pulls; drives the existing mesh
  enrichment pipeline via `JobRunner`.
- **Analytics REST** — thin shapers over `hitorro-fleet-retrieval` for
  dashboards (time histograms, top senders / domains / entities, threads,
  action candidates, trends).
- **Durable queries + alerts** — saved queries, cron-scheduled alert rules
  with fingerprint-delta detection, three delivery channels (email, webhook,
  in-app inbox), retry + backoff.
- **Reports** — scheduled query bundles that materialize durable artifacts.
- **Enrichment suggestions** — passive audit of your queries → proposals for
  new JVS fields / enricher steps to add to the pipeline.

## Layout

```
hitorro-fleet-mail-analytics/
├── pom.xml                  parent aggregator
├── backend/                 Spring Boot 3.2, Java 21, JPA/Hibernate
│   ├── pom.xml
│   └── src/main/{java,resources}/
└── frontend/                pnpm workspace: packages/core + packages/mail-analytics-app
```

The backend drives the frontend build via `frontend-maven-plugin`
(same pattern as `hitorro-search-ui`). Node + pnpm are downloaded into
`backend/target/` — nothing on `PATH` required.

## Modes

Two Spring profiles switch datasource + delivery wiring; same jar, same code.

| profile      | db       | mail source     | SMTP        | intended target      |
|--------------|----------|-----------------|-------------|----------------------|
| `standalone` | H2 file  | Mac Mail SQLite | local relay | laptop, dev          |
| `clustered`  | Postgres | IMAP            | external    | Orion / K8s + fleet  |

Select with `--spring.profiles.active=standalone` (default) or `clustered`.

## Build

```bash
# Full build (backend + React bundle)
mvn -pl hitorro-fleet-mail-analytics/backend -am clean install

# Backend only (skips node/pnpm download + React build)
mvn -pl hitorro-fleet-mail-analytics/backend -am clean install -Dskip.frontend=true
```

## Run (standalone)

```bash
# 1. bring up fleet-retrieval on :8095 (see hitorro-fleet-retrieval README)
# 2. then:
export HT_HOME=~/hitorro
java -jar hitorro-fleet-mail-analytics/backend/target/hitorro-fleet-mail-analytics-3.0.1.jar
```

Health: `GET http://localhost:8100/api/health`. React SPA served from `/`.

## Endpoints (backend)

Grouped:

- `/api/dashboard/*` — overview, histogram, top-N, action-candidates, trends
- `/api/search/mail` — free-text + facet search
- `/api/senders/{email}/*` — timeline, related
- `/api/domains/{domain}/*` — timeline, senders
- `/api/topics/*` — entity / noun rollups
- `/api/clusters/*` — threads, sender clusters
- `/api/saved-queries` — CRUD + promote-to-alert / promote-to-report
- `/api/alerts` — CRUD + firings feed + mute
- `/api/inbox` — durable in-app inbox
- `/api/reports` — CRUD + run-now + artifact download
- `/api/webhooks` — CRUD + test
- `/api/ingest/sources` — CRUD + status + backfill + pause / resume
- `/api/suggestions` — enrichment suggestions
- `/api/summary/{thread,entity}` — LLM summaries with style selection
- `/api/summary/styles` — enumerate summary styles
- `/api/messages/{id}` — single-message body fetch (used by the Threads UI expand)
- `/api/settings` — delivery + source config

## Summarization

Threads and Topics can be summarized by an LLM in one of six styles
(BRIEF, CONTRIBUTIONS, ACTION_ITEMS, DECISIONS, SENTIMENT, ENTITIES).
Analytics is a thin caller — the style catalogue + prompt templates
live in `hitorro-retrieval`'s
[`SummaryStyles`](../hitorro-retrieval/src/main/java/com/hitorro/retrieval/pipeline/stages/SummaryStyles.java)
class and are served by `hitorro-fleet-retrieval`'s
[`/api/retrieval/summarize`](../hitorro-fleet-retrieval/README.md#summarization)
endpoint. Requires Ollama on `${mailanalytics.retrieval.base-url}`'s
configured URL (the fleet-retrieval process, not this one).

Adding a new style is a 3-line change in `hitorro-retrieval` — see
[docs/summarization.md](../hitorro-retrieval/docs/summarization.md).

## Ingest strategy

The mesh pipeline remains the enrichment engine of record. Analytics is its
**conductor**: reads watermark → fetches delta from the mail source → writes
NDJSON into `${mailanalytics.pipelines.hot-dir}/{source}/{batch}.ndjson` →
invokes `JobRunner.run(spec, status)` on the `mail-enrich-from-ndjson.yaml`
template → advances watermark on success. Failure holds the watermark; next
run retries the same batch.

## Not this module's job

- Full-text index / KV storage — belongs to the mesh pipeline sinks.
- Query coordination (facet merging, KV fallback, pagination) — belongs to
  `hitorro-fleet-retrieval`.
- NLP enrichment — belongs to `hitorro-jsontypesystem`'s jvs-enrich step,
  invoked by the mesh pipeline.
