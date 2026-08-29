import { create } from 'zustand';

type WindowMode = '24h' | '7d' | '30d' | '90d' | 'custom';

interface AnalyticsState {
  windowMode: WindowMode;
  from: string;
  to: string;
  setWindow: (mode: WindowMode, from?: string, to?: string) => void;
  effectiveRange: () => { from: string; to: string };
}

// Compute a from/to pair for a fixed window. Called only when the window
// mode changes — NOT on every render. Otherwise `new Date().toISOString()`
// produces a different value each render, invalidates the React Query
// key, and the UI never stops refetching.
function computeRange(mode: WindowMode, existingFrom?: string, existingTo?: string): { from: string; to: string } {
  if (mode === 'custom') {
    return { from: existingFrom || '', to: existingTo || '' };
  }
  const now = new Date();
  const days = mode === '24h' ? 1 : mode === '7d' ? 7 : mode === '30d' ? 30 : 90;
  const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: now.toISOString() };
}

const initial = computeRange('30d');

export const useAnalyticsStore = create<AnalyticsState>((set, get) => ({
  windowMode: '30d',
  from: initial.from,
  to: initial.to,
  setWindow: (mode, from, to) => {
    const r = computeRange(mode, from, to);
    set({ windowMode: mode, from: r.from, to: r.to });
  },
  // Kept for callers that still use it — returns the stable stored range,
  // NOT a fresh computation.
  effectiveRange: () => {
    const s = get();
    return { from: s.from, to: s.to };
  }
}));
