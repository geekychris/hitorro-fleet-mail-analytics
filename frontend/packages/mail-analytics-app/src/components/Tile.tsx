import React from 'react';

interface Props { title: string; children: React.ReactNode; action?: React.ReactNode; }
export default function Tile({ title, children, action }: Props) {
  return (
    <div className="bg-panel border border-border rounded p-4">
      <div className="flex justify-between items-center mb-3">
        <h3 className="text-sm font-semibold text-muted uppercase tracking-wide">{title}</h3>
        {action}
      </div>
      {children}
    </div>
  );
}
