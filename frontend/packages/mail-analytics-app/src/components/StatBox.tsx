interface Props { label: string; value: number | string; hint?: string; }
export default function StatBox({ label, value, hint }: Props) {
  return (
    <div className="bg-panel border border-border rounded p-4">
      <div className="text-xs text-muted uppercase tracking-wide">{label}</div>
      <div className="text-3xl font-bold mt-1">{typeof value === 'number' ? value.toLocaleString() : value}</div>
      {hint && <div className="text-xs text-muted mt-1">{hint}</div>}
    </div>
  );
}
