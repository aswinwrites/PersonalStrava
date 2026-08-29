import { format, startOfMonth, startOfWeek } from 'date-fns'
import { Footprints, Activity as JoggingIcon, Bike, Zap, Mountain, Clock } from 'lucide-react'
import { supabase } from '../lib/supabaseClient'
import { useSupabaseQuery } from '../lib/useSupabaseQuery'
import { formatDuration, formatKm, formatSteps } from '../lib/units'
import { StatTile } from '../components/StatTile'
import { MovementRing } from '../components/MovementRing'
import { useAuth } from '../features/auth/AuthProvider'
import { useCountUp } from '../lib/useCountUp'
import type { LifetimeTotals, PeriodTotals } from '../types/database'

const todayIso = () => format(new Date(), 'yyyy-MM-dd')

function useGreeting() {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

function AnimatedKm({ meters, fractionDigits = 0 }: { meters: number | undefined; fractionDigits?: number }) {
  const animated = useCountUp(meters ?? null)
  if (animated === null) return <>—</>
  return <>{(animated / 1000).toFixed(fractionDigits)} km</>
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
  const displayName = user?.user_metadata?.full_name?.split(' ')?.[0] ?? user?.user_metadata?.name?.split(' ')?.[0]

  return (
    <div className="flex flex-col gap-10">
      {/* Hero */}
      <section className="flex flex-col items-center gap-6 rounded-3xl border border-[var(--color-border)] bg-gradient-to-b from-[var(--color-surface)] to-[var(--color-paper)] px-6 py-8 text-center sm:flex-row sm:justify-between sm:text-left">
        <div>
          <p className="text-sm text-[var(--color-muted)]">
            {greeting}
            {displayName ? `, ${displayName}` : ''}
          </p>
          <div className="mt-2 flex flex-wrap items-baseline justify-center gap-x-4 gap-y-1 sm:justify-start">
            <span className="text-xs font-medium text-[var(--color-walking)]">
              <AnimatedKm meters={t?.walking_distance_meters} fractionDigits={1} /> walked
            </span>
            <span className="text-xs font-medium text-[var(--color-jogging)]">
              <AnimatedKm meters={t?.jogging_distance_meters} fractionDigits={1} /> jogged
            </span>
            <span className="text-xs font-medium text-[var(--color-cycling)]">
              <AnimatedKm meters={t?.cycling_distance_meters} fractionDigits={1} /> cycled
            </span>
            <span className="text-xs font-medium text-[var(--color-motorcycling)]">
              <AnimatedKm meters={t?.motorcycling_distance_meters} fractionDigits={1} /> ridden
            </span>
          </div>
        </div>
        <MovementRing
          walkingSeconds={t?.walking_seconds ?? 0}
          joggingSeconds={t?.jogging_seconds ?? 0}
          cyclingSeconds={t?.cycling_seconds ?? 0}
          motorcyclingSeconds={t?.motorcycling_seconds ?? 0}
          steps={t?.steps ?? 0}
        />
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold tracking-tight text-[var(--color-muted)]">This week</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          <StatTile label="Steps" value={w ? formatSteps(w.steps) : '—'} icon={Footprints} accent="walking" />
          <StatTile
            label="Active time"
            value={w ? formatDuration(w.walking_seconds + w.jogging_seconds + w.cycling_seconds + w.motorcycling_seconds) : '—'}
            icon={Clock}
          />
          <StatTile label="Jogging" value={w ? formatKm(w.jogging_distance_meters) : '—'} icon={JoggingIcon} accent="jogging" />
          <StatTile label="Cycling" value={w ? formatKm(w.cycling_distance_meters) : '—'} icon={Bike} accent="cycling" />
          <StatTile label="Motorcycling" value={w ? formatKm(w.motorcycling_distance_meters) : '—'} icon={Zap} accent="motorcycling" />
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold tracking-tight text-[var(--color-muted)]">This month</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          <StatTile label="Steps" value={m ? formatSteps(m.steps) : '—'} icon={Footprints} accent="walking" />
          <StatTile
            label="Active time"
            value={m ? formatDuration(m.walking_seconds + m.jogging_seconds + m.cycling_seconds + m.motorcycling_seconds) : '—'}
            icon={Clock}
          />
          <StatTile label="Jogging" value={m ? formatKm(m.jogging_distance_meters) : '—'} icon={JoggingIcon} accent="jogging" />
          <StatTile label="Cycling" value={m ? formatKm(m.cycling_distance_meters) : '—'} icon={Bike} accent="cycling" />
          <StatTile label="Motorcycling" value={m ? formatKm(m.motorcycling_distance_meters) : '—'} icon={Zap} accent="motorcycling" />
        </div>
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold tracking-tight text-[var(--color-muted)]">Lifetime</h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          <StatTile label="Walking" value={l ? formatKm(l.walking_distance_meters, 0) : '—'} icon={Footprints} accent="walking" />
          <StatTile label="Jogging" value={l ? formatKm(l.jogging_distance_meters, 0) : '—'} icon={JoggingIcon} accent="jogging" />
          <StatTile label="Cycling" value={l ? formatKm(l.cycling_distance_meters, 0) : '—'} icon={Bike} accent="cycling" />
          <StatTile label="Motorcycling" value={l ? formatKm(l.motorcycling_distance_meters, 0) : '—'} icon={Zap} accent="motorcycling" />
          <StatTile label="Total distance" value={l ? formatKm(l.total_distance_meters, 0) : '—'} icon={Mountain} />
        </div>
      </section>
    </div>
  )
}
