import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { suggestions } from '../api/client';
import Tile from '../components/Tile';

// Per-suggestion-kind scaffold. What "Apply" means depends on the kind:
// UNINDEXED_FILTER → add a field entry with a `groups.index` block
// HIGH_FREQ_NER   → same, driven by which NE_ tag was hot
// MISSING_*_EXTRACT → propose a jvs-enrich mapper wiring
// MISSING_NEWSLETTER_FLAG → propose extending the LIST constant in the pipeline yaml
function templateFor(s: any): { title: string; language: string; body: string } {
  const field = s.targetField || 'newfield';
  switch (s.kind) {
    case 'UNINDEXED_FILTER':
      return {
        title: `Add "${field}" to config/types/mail_email.json`,
        language: 'json',
        body: JSON.stringify({
          name: field,
          type: 'core_string',
          groups: [{ name: 'index', method: 'identifier' }]
        }, null, 2)
      };
    case 'HIGH_FREQ_NER':
      return {
        title: `Promote ${field} to first-class extracted field`,
        language: 'yaml',
        body: `# Suggested pipeline addition — enrich step + type field for ${field}
steps:
  - kind: groovy-map
    script: |
      # extract ${field} tokens from body.mls[0].segmented_ner
      # and copy to top-level '${field.toLowerCase()}_entities' (core_string list)
`
      };
    case 'MISSING_URL_EXTRACT':
      return {
        title: 'Add URL extractor to the enrichment pipeline',
        language: 'yaml',
        body: `# Insert into your job YAML after the groovy-map step:
- kind: groovy-map
  script: |
    def urls = []
    def m = (row.body_preview ?: '') =~ ~/https?:\\S+/
    while (m.find()) urls.add(m.group(0))
    if (urls) row.urls = urls
    row
`
      };
    case 'MISSING_PHONE_EXTRACT':
      return {
        title: 'Add phone-number extractor to the pipeline',
        language: 'yaml',
        body: `# Insert into your job YAML after the groovy-map step:
- kind: groovy-map
  script: |
    def phones = []
    def m = (row.body_preview ?: '') =~ ~/(?:\\+?\\d{1,3}[\\s-]?)?\\(?\\d{3}\\)?[\\s.-]\\d{3}[\\s.-]\\d{4}/
    while (m.find()) phones.add(m.group(0))
    if (phones) row.phones = phones
    row
`
      };
    case 'MISSING_TRACKING_EXTRACT':
      return {
        title: 'Add tracking-number extractor to the pipeline',
        language: 'yaml',
        body: `# Insert into your job YAML after the groovy-map step:
- kind: groovy-map
  script: |
    def tracks = []
    def m = (row.body_preview ?: '') =~ ~/\\b(?:1Z[0-9A-Z]{16}|\\d{12,22})\\b/
    while (m.find()) tracks.add(m.group(0))
    if (tracks) row.tracking = tracks
    row
`
      };
    case 'MISSING_NEWSLETTER_FLAG':
      return {
        title: `Add "${field}" to the newsletter list in your ingest yaml`,
        language: 'yaml',
        body: `# Extend LIST in your ingest pipeline groovy-map:
def LIST = ['mailchimpapp.com','substack.com', /* existing entries... */,
            '${field}']
`
      };
    default:
      return {
        title: 'Suggestion detail',
        language: 'text',
        body: s.rationale || 'No template available for this kind.'
      };
  }
}

export default function Suggestions() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ['suggestions'], queryFn: suggestions.list });
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const invalidate = () => qc.invalidateQueries({ queryKey: ['suggestions'] });
  const dismiss = useMutation({ mutationFn: suggestions.dismiss, onSuccess: invalidate });
  const reviewed = useMutation({ mutationFn: suggestions.reviewed, onSuccess: invalidate });
  const implemented = useMutation({
    mutationFn: (id: number) =>
      fetch(`/api/suggestions/${id}/implemented`, { method: 'POST' }).then((r) => r.json()),
    onSuccess: () => { invalidate(); setExpandedId(null); }
  });
  const runNow = useMutation({ mutationFn: suggestions.runNow, onSuccess: invalidate });

  const copy = async (id: number, text: string) => {
    try { await navigator.clipboard.writeText(text); } catch { /* http fallback */ }
    setCopiedId(id);
    setTimeout(() => setCopiedId((c) => (c === id ? null : c)), 1500);
  };

  return (
    <Tile
      title="enrichment suggestions"
      action={
        <button
          className="text-xs bg-accent text-surface font-semibold px-2 py-1 rounded"
          onClick={() => runNow.mutate()}
        >
          scan now
        </button>
      }
    >
      {data?.length ? (
        <div className="divide-y divide-border">
          {data.map((s: any) => {
            const isOpen = expandedId === s.id;
            const tpl = templateFor(s);
            return (
              <div key={s.id} className="py-3">
                <div className="flex justify-between items-baseline">
                  <div>
                    <span className="text-xs bg-warn text-surface px-2 py-0.5 rounded font-bold mr-2">
                      {s.kind}
                    </span>
                    <span className="font-semibold">{s.title}</span>
                  </div>
                  <div className="flex gap-2 text-xs">
                    <button
                      className="text-accent"
                      onClick={() => setExpandedId(isOpen ? null : s.id)}
                    >
                      {isOpen ? 'hide' : 'apply →'}
                    </button>
                    <button className="text-accent" onClick={() => reviewed.mutate(s.id)}>
                      reviewed
                    </button>
                    <button className="text-crit" onClick={() => dismiss.mutate(s.id)}>
                      dismiss
                    </button>
                  </div>
                </div>
                <div className="text-sm text-muted mt-1">{s.rationale}</div>
                {isOpen && (
                  <div className="mt-3 bg-surface border border-border rounded p-3">
                    <div className="flex justify-between items-center mb-2">
                      <div className="text-xs font-semibold text-muted uppercase">
                        {tpl.title}
                        <span className="ml-2 text-[10px] normal-case text-muted">
                          ({tpl.language})
                        </span>
                      </div>
                      <div className="flex gap-2">
                        <button
                          className="text-xs text-accent"
                          onClick={() => copy(s.id, tpl.body)}
                        >
                          {copiedId === s.id ? '✓ copied' : 'copy'}
                        </button>
                        <button
                          className="text-xs bg-accent text-surface font-semibold px-2 py-0.5 rounded"
                          onClick={() => implemented.mutate(s.id)}
                        >
                          mark implemented
                        </button>
                      </div>
                    </div>
                    <pre className="text-xs overflow-auto whitespace-pre-wrap font-mono">
{tpl.body}
                    </pre>
                    <div className="text-xs text-muted mt-2">
                      Apply the snippet above to the referenced file, redeploy the pipeline,
                      then click <em>mark implemented</em> to remove this suggestion.
                    </div>
                  </div>
                )}
                {s.evidenceJson && (
                  <details className="text-xs mt-1">
                    <summary className="text-muted cursor-pointer">evidence</summary>
                    <pre className="text-xs bg-surface p-2 rounded mt-1 overflow-auto">
                      {s.evidenceJson}
                    </pre>
                  </details>
                )}
              </div>
            );
          })}
        </div>
      ) : (
        <div className="text-muted text-sm">
          no open suggestions — the scanner runs every 30 min or click "scan now"
        </div>
      )}
    </Tile>
  );
}
