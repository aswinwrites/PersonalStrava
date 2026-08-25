import { format, startOfMonth, startOfWeek } from 'date-fns'
import { supabase } from '../lib/supabaseClient'
import { useSupabaseQuery } from '../lib/useSupabaseQuery'
import { formatDuration, formatKm, formatSteps } from '../lib/units'
import { StatTile } from '../components/StatTile'
import { useAuth } from '../features/auth/AuthProvider'
import type { LifetimeTotals, PeriodTotals } from '../types/database'

const todayIso = () => format(new Date(), 'yyyy-MM-dd')

function useGreeting() {
  const hour = new Date().getHours()
  if (hour < 12) return 'GOOD MORNING'
  if (hour < 18) return 'GOOD AFTERNOON'
  return 'GOOD EVENING'
}

export function HomePage() {
  const { user } = useAuth()
  const greeting = useGreeting()
  const today = todayIso()
  const weekStart = format(startOfWeek(new Date(), { weekStartsOn: 1 }), 'yyyy-MM-dd')
  const monthStart = format(startOfMonth(new Date()), 'yyyy-MM-dd')

  const todayQuery = useSupabaseQuery<PeriodTotals>(
    () =>
      supabase
        .rpc('get_period_totals', { period_start: today, period_end: today })
        .then((r) => ({ data: (r.data as PeriodTotals[] | null)?.[0] ?? null, error: r.error })),
    [today, user?.id],
  )
  const weekQuery = useSupabaseQuery<PeriodTotals>(
    () =>
      supabase
        .rpc('get_period_totals', { period_start: weekStart, period_end: today })
        .then((r) => ({ data: (r.data as PeriodTotals[] | null)?.[0] ?? null, error: r.error })),
    [weekStart, today, user?.id],
  )
  const monthQuery = useSupabaseQuery<PeriodTotals>(
    () =>
      supabase
        .rpc('get_period_totals', { period_start: monthStart, period_end: today })
        .then((r) => ({ data: (r.data as PeriodTotals[] | null)?.[0] ?? null, error: r.error })),
    [monthStart, today, user?.id],
  )
  const lifetimeQuery = useSupabaseQuery<LifetimeTotals>(
    () => supabase.rpc('get_lifetime_totals').then((r) => ({ data: (r.data as LifetimeTotals[] | null)?.[0] ?? null, error: r.error })),
    [user?.id],
  )

  const t = todayQuery.data
  const w = weekQuery.data
  const m = monthQuery.data
  const l = lifetimeQuery.data

  return (
    <div className="flex flex-col gap-8">
      <section>
        <p className="text-xs font-medium tracking-widest text-[var(--color-muted)]">{greeting}</p>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatTile label="Steps today" value={t ? formatSteps(t.steps) : '—'} accent="walking" />
          <StatTile label="Cycling today" value={t ? formatKm(t.cycling_distance_meters) : '—'} accent="cycling" />
          <StatTile label="Motorcycling today" value={t ? formatKm(t.motorcycling_distance_meters) : '—'} accent="motorcycling" />
          <StatTile label="Elevation today" value={t ? `+${Math.round(t.elevation_gain_meters)}m` : '—'} />
        </div>
      </section>

      <section>
        <h2 className="text-sm font-semibold tracking-tight">This week</h2>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatTile label="Steps" value={w ? formatSteps(w.steps) : '—'} />
          <StatTile label="Active time" value={w ? formatDuration(w.walking_seconds + w.cycling_seconds + w.motorcycling_seconds) : '—'} />
          <StatTile label="Cycling" value={w ? formatKm(w.cycling_distance_meters) : '—'} accent="cycling" />
          <StatTile label="Motorcycling" value={w ? formatKm(w.motorcycling_distance_meters) : '—'} accent="motorcycling" />
        </div>
      </section>

      <section>
        <h2 className="text-sm font-semibold tracking-tight">This month</h2>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatTile label="Steps" value={m ? formatSteps(m.steps) : '—'} />
          <StatTile label="Active time" value={m ? formatDuration(m.walking_seconds + m.cycling_seconds + m.motorcycling_seconds) : '—'} />
          <StatTile label="Cycling" value={m ? formatKm(m.cycling_distance_meters) : '—'} accent="cycling" />
          <StatTile label="Motorcycling" value={m ? formatKm(m.motorcycling_distance_meters) : '—'} accent="motorcycling" />
        </div>
      </section>

      <section>
        <h2 className="text-sm font-semibold tracking-tight">Lifetime</h2>
        <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatTile label="Walking" value={l ? formatKm(l.walking_distance_meters, 0) : '—'} accent="walking" />
          <StatTile label="Cycling" value={l ? formatKm(l.cycling_distance_meters, 0) : '—'} accent="cycling" />
          <StatTile label="Motorcycling" value={l ? formatKm(l.motorcycling_distance_meters, 0) : '—'} accent="motorcycling" />
          <StatTile label="Total distance" value={l ? formatKm(l.total_distance_meters, 0) : '—'} />
        </div>
      </section>
    </div>
  )
}
