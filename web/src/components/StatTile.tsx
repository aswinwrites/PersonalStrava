import type { LucideIcon } from 'lucide-react'

type Accent = 'walking' | 'cycling' | 'motorcycling'

const ACCENT_GRADIENT: Record<Accent, string> = {
  walking: 'from-[var(--color-walking)]/12',
  cycling: 'from-[var(--color-cycling)]/12',
  motorcycling: 'from-[var(--color-motorcycling)]/12',
}

interface StatTileProps {
  label: string
  value: string
  accent?: Accent
  icon?: LucideIcon
  /** Optional signed trend, e.g. "+12%" — rendered with an up/down tint. */
  trend?: string
}

export function StatTile({ label, value, accent, icon: Icon, trend }: StatTileProps) {
  const accentColor = accent ? `var(--color-${accent})` : 'var(--color-ink)'
  const gradient = accent ? ACCENT_GRADIENT[accent] : 'from-[var(--color-ink)]/6'
  const trendUp = trend?.startsWith('+')

  return (
    <div
      className={`group relative overflow-hidden rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-black/5`}
    >
      <div className={`pointer-events-none absolute inset-0 bg-gradient-to-br ${gradient} to-transparent opacity-0 transition-opacity duration-300 group-hover:opacity-100`} />
      <div className="relative flex items-start justify-between">
        <div className="text-2xl font-semibold tracking-tight font-mono-stat" style={{ color: accentColor }}>
          {value}
        </div>
        {Icon && (
          <Icon
            size={16}
            className="text-[var(--color-muted)] opacity-60 transition-opacity group-hover:opacity-100"
            style={{ color: accent ? accentColor : undefined }}
          />
        )}
      </div>
      <div className="relative mt-1 flex items-center gap-1.5">
        <span className="text-xs uppercase tracking-wide text-[var(--color-muted)]">{label}</span>
        {trend && (
          <span className={`text-[11px] font-medium ${trendUp ? 'text-[var(--color-walking)]' : 'text-[var(--color-cycling)]'}`}>
            {trend}
          </span>
        )}
      </div>
    </div>
  )
}
