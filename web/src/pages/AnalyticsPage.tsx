import { useState } from 'react'
import { format, subDays, subMonths, subYears } from 'date-fns'
import { supabase } from '../lib/supabaseClient'
import { useSupabaseQuery } from '../lib/useSupabaseQuery'
import { StatTile } from '../components/StatTile'
import { formatDuration, formatKm, formatSteps } from '../lib/units'
import type { PeriodTotals } from '../types/database'

const RANGES = ['7D', '30D', '3M', '6M', '1Y', 'ALL'] as const
type Range = (typeof RANGES)[number]

function rangeStart(range: Range): string {
  const now = new Date()
  switch (range) {
    case '7D':
      return format(subDays(now, 7), 'yyyy-MM-dd')
    case '30D':
      return format(subDays(now, 30), 'yyyy-MM-dd')
    case '3M':
      return format(subMonths(now, 3), 'yyyy-MM-dd')
    case '6M':
      return format(subMonths(now, 6), 'yyyy-MM-dd')
    case '1Y':
      return format(subYears(now, 1), 'yyyy-MM-dd')
    case 'ALL':
      return '1970-01-01'
  }
}

export function AnalyticsPage() {
  const [range, setRange] = useState<Range>('30D')
  const today = format(new Date(), 'yyyy-MM-dd')
  const start = rangeStart(range)

  const { data: totals } = useSupabaseQuery<PeriodTotals>(
    () =>
      supabase
        .rpc('get_period_totals', { period_start: start, period_end: today })
        .then((r) => ({ data: (r.data as PeriodTotals[] | null)?.[0] ?? null, error: r.error })),
    [start, today],
  )

  return (
    <div className="flex flex-col gap-6">
      <div className="flex gap-1 overflow-x-auto">
        {RANGES.map((r) => (
          <button
            key={r}
            onClick={() => setRange(r)}
            className={`rounded-full px-3 py-1.5 text-xs font-medium ${
              range === r ? 'bg-[var(--color-ink)] text-[var(--color-paper)]' : 'border border-[var(--color-border)]'
            }`}
          >
            {r}
          </button>
        ))}
      </div>

      <section>
        <h2 className="text-sm font-semibold tracking-tight">Overall</h2>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatTile
            label="Total distance"
            value={
              totals
                ? formatKm(totals.walking_distance_meters + totals.cycling_distance_meters + totals.motorcycling_distance_meters)
                : '—'
            }
          />
          <StatTile label="Active time" value={totals ? formatDuration(totals.walking_seconds + totals.cycling_seconds + totals.motorcycling_seconds) : '—'} />
          <StatTile label="Steps" value={totals ? formatSteps(totals.steps) : '—'} accent="walking" />
          <StatTile label="Activities" value={totals ? String(totals.activity_count) : '—'} />
        </div>
      </section>

      <section>
        <h2 className="text-sm font-semibold tracking-tight">Walking</h2>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatTile label="Steps" value={totals ? formatSteps(totals.steps) : '—'} accent="walking" />
          <StatTile label="Distance" value={totals ? formatKm(totals.walking_distance_meters) : '—'} accent="walking" />
          <StatTile label="Active time" value={totals ? formatDuration(totals.walking_seconds) : '—'} accent="walking" />
        </div>
      </section>

      <section>
        <h2 className="text-sm font-semibold tracking-tight">Cycling</h2>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatTile label="Distance" value={totals ? formatKm(totals.cycling_distance_meters) : '—'} accent="cycling" />
          <StatTile label="Time" value={totals ? formatDuration(totals.cycling_seconds) : '—'} accent="cycling" />
          <StatTile label="Elevation" value={totals ? `+${Math.round(totals.elevation_gain_meters)}m` : '—'} accent="cycling" />
        </div>
        <p className="mt-2 text-xs text-[var(--color-muted)]">
          Per-ride breakdowns (avg speed, longest ride, max speed) come from the activities table directly — wired up in
          Phase 2 alongside the trend charts (this vs. last period).
        </p>
      </section>

      <section>
        <h2 className="text-sm font-semibold tracking-tight">Motorcycling</h2>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatTile label="Distance" value={totals ? formatKm(totals.motorcycling_distance_meters) : '—'} accent="motorcycling" />
          <StatTile label="Time" value={totals ? formatDuration(totals.motorcycling_seconds) : '—'} accent="motorcycling" />
        </div>
      </section>
    </div>
  )
}
