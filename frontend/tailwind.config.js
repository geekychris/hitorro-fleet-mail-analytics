/** @type {import('tailwindcss').Config} */
export default {
  content: ['./packages/**/index.html', './packages/**/src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        surface: '#0f172a',
        panel: '#1e293b',
        border: '#334155',
        text: '#e2e8f0',
        muted: '#94a3b8',
        accent: '#38bdf8',
        warn: '#f59e0b',
        crit: '#ef4444'
      }
    }
  },
  plugins: []
};
