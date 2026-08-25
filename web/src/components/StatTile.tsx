export function StatTile({ label, value, accent }: { label: string; value: string; accent?: 'walking' | 'cycling' | 'motorcycling' }) {
  const accentColor = accent ? `var(--color-${accent})` : 'var(--color-ink)'
  return (
    <div className="rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <div className="text-2xl font-semibold tracking-tight font-mono-stat" style={{ color: accentColor }}>
        {value}
      </div>
      <div className="mt-1 text-xs uppercase tracking-wide text-[var(--color-muted)]">{label}</div>
    </div>
  )
}
