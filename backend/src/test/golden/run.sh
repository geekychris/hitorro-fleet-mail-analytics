#!/bin/bash
# Golden REST test for hitorro-fleet-mail-analytics, against responses recorded from main on Jackson 2
# (main/main-*.jsonl.gz: 5 runs of main @ 4b24f5c, with fleet-retrieval main @ 20412aa).
#
#   backend/src/test/golden/run.sh <mail-analytics.jar> <fleet-retrieval jar or classpath> <h2.jar> [ui-out.json]
#     HITORRO_CONFIG=<hitorro-all>/config   MAIL_TYPES=<hitorro-mesh-pipelines-sqlite>/src/main/resources/types
#     (both default to sibling checkouts; ui-out needs node + playwright, see ui_routes.js)
#
# Starts fleet-retrieval (standalone) holding a 'mail' index of six mail_email documents, a logging proxy
# in front of it, a webhook capture server and the app; makes 89 requests (every controller: dashboard,
# search, senders, domains, messages, topics, threads, summary, ingest sources, saved queries, alerts
# incl. run-now with inbox+webhook delivery, inbox, webhooks incl. test, reports incl. run-now and
# artifacts, suggestions, actuator, SPA) and records each response plus the requests the app made to
# fleet-retrieval and webhooks meanwhile, then the 11 H2 tables. Compared with the main recordings
# (byte for byte after masking run values; order-insensitively where main's order varies), except the
# records in accepted-4.0.0.jsonl.gz: deliberate differences (java.time now serializable: report runs
# and webhook alert delivery succeed where main failed), compared with that recording of 4.0.0.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../../.." && pwd)
work=$(mktemp -d)
UI=(); [ $# -ge 4 ] && UI=(--ui "$4" --ui-dir "$here")
python3 "$here/mail_golden.py" --cp "$1" --retrieval-cp "$2" --h2-jar "$3" --requests "$here/requests.py" \
  --config "${HITORRO_CONFIG:-$root/../hitorro-all/config}" \
  --types "${MAIL_TYPES:-$root/../hitorro-mesh-pipelines-sqlite/src/main/resources/types}" \
  --out "$work/new.jsonl" --work "$work/w" ${UI[@]+"${UI[@]}"}
python3 "$here/golden_compare.py" "$(ls "$here"/main/main-*.jsonl.gz | paste -sd, -)" "$work/new.jsonl" \
  --accepted="$here/accepted-4.0.0.jsonl.gz"
