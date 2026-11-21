# Request list for hitorro-fleet-mail-analytics (standalone profile, offline: no Ollama, no SMTP).

TABLES = ['watermark', 'saved_query', 'alert_rule', 'alert_firing', 'alert_delivery', 'inbox_item',
          'webhook_config', 'report', 'report_run', 'enrichment_suggestion', 'query_audit']

def ARGS(work):
    return ['--mailanalytics.summary.enabled=false', '--spring.ai.ollama.base-url=http://127.0.0.1:9',
            f'--mailanalytics.pipelines.home={work}/pipelines', f'--mailanalytics.pipelines.hot-dir={work}/hot',
            # no scheduled ingest during the run (the source's cron would fire every 5 minutes)
            # (Spring doesn't merge list elements across sources: restate the whole standalone entry)
            '--mailanalytics.ingest.sources[0].id=mac-mail', '--mailanalytics.ingest.sources[0].kind=sqlite',
            '--mailanalytics.ingest.sources[0].enabled=true', '--mailanalytics.ingest.sources[0].cron=0 0 0 1 1 *',
            f'--mailanalytics.ingest.sources[0].config.path={work}/home/Library/Mail/V10/MailData/Envelope Index',
            '--mailanalytics.ingest.default-cron=0 0 0 1 1 *']

import datetime
H = 3600 * 1000
def iso(ms):
    return datetime.datetime.fromtimestamp(ms / 1000, datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
def mail(n, now, age_ms, sender, name, domain, subject, body, read=False, flagged=False, news=False, size=1000):
    return {"ht_type": "mail_email", "id": {"id": str(n), "did": str(n), "domain": "mac-mail"},
            "title": {"mls": [{"lang": "en", "text": subject}]}, "body": {"mls": [{"lang": "en", "text": body}]},
            "date_received": iso(now - age_ms if age_ms is not None else 1736000000000 + n * 86400000),
            "sender_address": sender, "sender_name": name, "sender_domain": domain,
            "mailbox_url": "imap://box/INBOX", "read": read, "flagged": flagged, "size_bytes": size,
            "recipient_count": 2, "is_newsletter": news}

def seed(now):
    docs = [mail(1, now, 1 * H, "ada@acme.com", "Ada Lovelace", "acme.com", "Quarterly report for Acme", "Please review the numbers, Ada"),
            mail(2, now, 2 * H, "bob@acme.com", "Bob Smith", "acme.com", "Re: Quarterly report", "Looks good. Bob", read=True),
            mail(3, now, 5 * H, "news@letters.io", "Letters", "letters.io", "Weekly digest été", "Top stories this week", news=True, size=50000),
            mail(4, now, 26 * H, "carol@example.org", "Carol", "example.org", "Meeting tomorrow in Paris", "Carol will meet Ada in Paris", flagged=True),
            mail(5, now, None, "ada@acme.com", "Ada Lovelace", "acme.com", "Old thread", "From last year"),
            mail(6, now, None, "dave@example.org", "Dave", "example.org", "Invoice 12345678901", "Amount due", size=12345678901)]
    return [('POST', '/api/ingest/indexes', {"name": "mail", "typeName": "mail_email"}),
            ('POST', '/api/ingest/indexes/mail/documents', docs),
            ('POST', '/api/ingest/indexes/mail/refresh', None)]

def requests(work, hook):
    sq = {"name": "acme mail", "description": "from acme", "dslJson": '{"index":"mail","text":"","filters":{"sender_domain":"acme.com"},"facets":["sender_address"],"limit":10}', "tags": "work,acme", "owner": "chris", "createdAt": "2026-01-02T03:04:05Z"}
    return [
      ('GET', '/actuator/health', None), ('GET', '/actuator/info', None), ('GET', '/api/health', None), ('GET', '/api/settings', None),
      ('GET', '/api/dashboard/overview', None), ('GET', '/api/dashboard/overview?daysBack=2', None),
      ('GET', '/api/dashboard/histogram?bucket=day', None), ('GET', '/api/dashboard/histogram?bucket=hour&daysBack=2', None),
      ('GET', '/api/dashboard/top-senders', None), ('GET', '/api/dashboard/top-domains?limit=2', None),
      ('GET', '/api/dashboard/top-entities?kind=PERSON', None), ('GET', '/api/dashboard/top-entities?kind=LOCATION', None),
      ('GET', '/api/dashboard/action-candidates', None), ('GET', '/api/dashboard/trends?window=7d', None), ('GET', '/api/dashboard/trends?window=bogus', None),
      ('GET', '/api/search/mail?q=report', None), ('GET', '/api/search/mail?q=', None), ('GET', '/api/search/mail?q=report&sort=date_received.date_s:asc&size=1', None),
      ('GET', '/api/search/mail?q=(((', None),
      ('GET', '/api/senders/ada@acme.com', None), ('GET', '/api/senders/ada@acme.com/messages', None), ('GET', '/api/senders/nobody@x.y', None),
      ('GET', '/api/domains/acme.com', None), ('GET', '/api/domains/acme.com/messages', None), ('GET', '/api/domains/acme.com/senders', None),
      ('GET', '/api/messages/mac-mail%2F1', None), ('GET', '/api/messages/1', None), ('GET', '/api/messages/nope', None),
      ('GET', '/api/topics/entities', None), ('GET', '/api/clusters/threads', None),
      ('GET', '/api/summary/styles', None), ('POST', '/api/summary/thread?key=report', None), ('POST', '/api/summary/entity?value=Ada', None),
      ('GET', '/api/ingest/sources', None), ('GET', '/api/ingest/sources/mac-mail/status', None), ('GET', '/api/ingest/sources/nope/status', None),
      # saved queries
      ('GET', '/api/saved-queries', None), ('POST', '/api/saved-queries', sq), ('GET', '/api/saved-queries/1', None),
      ('PUT', '/api/saved-queries/1', dict(sq, description="updated", extraUnknown=1)), ('POST', '/api/saved-queries/1/run', None),
      ('POST', '/api/saved-queries', '{"name": "bad", "dslJson": "{not json"}'), ('POST', '/api/saved-queries/2/run', None),
      ('GET', '/api/saved-queries/999', None), ('POST', '/api/saved-queries', '{bad json'),
      # webhooks
      ('POST', '/api/webhooks', {"name": "hook1", "url": hook, "secret": "s3cret", "headersJson": '{"X-Test":"yes"}', "enabled": True}),
      ('GET', '/api/webhooks', None), ('GET', '/api/webhooks/1', None), ('POST', '/api/webhooks/1/test', None),
      ('PUT', '/api/webhooks/1', {"name": "hook1", "url": hook, "secret": "s3cret", "headersJson": '{"X-Test":"yes"}', "enabled": True, "lastSuccessAt": 1767323045123}),
      # alerts (inbox + webhook channels)
      ('POST', '/api/alerts', {"savedQueryId": 1, "name": "acme alert", "cron": "0 0 * * * *", "deltaMode": "ANY_NEW",
                               "deliveryChannelsJson": '[{"channel":"inbox","user":"chris"},{"channel":"webhook","webhook":"hook1"},{"channel":"bogus"}]',
                               "enabled": True, "cooldownSeconds": 0}),
      ('POST', '/api/alerts', {"savedQueryId": 1, "name": "count alert", "cron": "0 0 * * * *", "deltaMode": "COUNT_THRESHOLD",
                               "thresholdConfigJson": '{"gte": 1}', "deliveryChannelsJson": '[{"channel":"inbox","user":"chris"}]'}),
      ('GET', '/api/alerts', None), ('GET', '/api/alerts/1', None),
      ('POST', '/api/alerts/1/run-now', None), ('POST', '/api/alerts/1/run-now', None), ('POST', '/api/alerts/2/run-now', None),
      ('GET', '/api/alerts/1/firings', None), ('GET', '/api/alerts/firings', None),
      ('POST', '/api/alerts/1/mute?minutes=30', None), ('POST', '/api/alerts/1/enable?enabled=false', None),
      ('PUT', '/api/alerts/2', {"savedQueryId": 1, "name": "count alert 2", "cron": "0 0 * * * *", "deltaMode": "VALUE_THRESHOLD", "mutedUntil": "2030-01-01T00:00:00Z"}),
      ('GET', '/api/alerts/999', None),
      # inbox
      ('GET', '/api/inbox', None), ('GET', '/api/inbox/unread-count', None), ('POST', '/api/inbox/1/read', None),
      ('POST', '/api/inbox/2/snooze?minutes=15', None), ('POST', '/api/inbox/2/dismiss', None), ('GET', '/api/inbox', None),
      # reports
      ('POST', '/api/reports', {"name": "daily", "description": "snap", "kind": "DASHBOARD_SNAPSHOT", "configJson": '{"daysBack": 2}', "cron": "0 0 6 * * *", "retentionDays": 7}),
      ('POST', '/api/reports', {"name": "sqset", "kind": "SAVED_QUERY_SET", "configJson": '{"savedQueryIds": [1]}', "cron": "0 0 6 * * *"}),
      ('GET', '/api/reports', None), ('GET', '/api/reports/1', None),
      ('POST', '/api/reports/1/run-now', None), ('POST', '/api/reports/2/run-now', None),
      ('GET', '/api/reports/1/runs', None), ('GET', '/api/reports/1/runs/1/artifact', None), ('GET', '/api/reports/2/runs/2/artifact', None),
      ('POST', '/api/reports', '{"name": "x", "kind": "NOPE"}'),
      # suggestions
      ('GET', '/api/suggestions', None), ('POST', '/api/suggestions/run-now', None), ('GET', '/api/suggestions', None),
      ('POST', '/api/suggestions/1/reviewed', None), ('POST', '/api/suggestions/999/dismiss', None),
      # deletes
      ('DELETE', '/api/alerts/2', None), ('DELETE', '/api/reports/2', None), ('DELETE', '/api/webhooks/999', None),
      ('GET', '/no/such/api', None), ('GET', '/', None),
    ]
