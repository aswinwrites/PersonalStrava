import { useState } from 'react'
import { supabase } from '../lib/supabaseClient'
import { useSupabaseQuery } from '../lib/useSupabaseQuery'
import { EmptyState } from '../components/EmptyState'
import { formatDuration, formatKm, formatKmh } from '../lib/units'
import type { ActivityRow, ActivityType } from '../types/database'

const FILTERS: Array<{ label: string; value: ActivityType | 'all' }> = [
  { label: 'All', value: 'all' },
  { label: 'Walking', value: 'walking' },
  { label: 'Cycling', value: 'cycling' },
  { label: 'Motorcycling', value: 'motorcycling' },
]

const PAGE_SIZE = 20

export function ActivitiesPage() {
  const [filter, setFilter] = useState<ActivityType | 'all'>('all')
  const [page, setPage] = useState(0)

  const { data: activities, loading } = useSupabaseQuery<ActivityRow[]>(() => {
    let query = supabase
      .from('activities')
      .select('*')
      .order('start_time', { ascending: false })
      .range(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE - 1)
    if (filter !== 'all') query = query.eq('activity_type', filter)
    return query
  }, [filter, page])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex gap-1">
        {FILTERS.map((f) => (
          <button
            key={f.value}
            onClick={() => {
              setFilter(f.value)
              setPage(0)
            }}
            className={`rounded-full px-3 py-1.5 text-xs font-medium ${
              filter === f.value ? 'bg-[var(--color-ink)] text-[var(--color-paper)]' : 'border border-[var(--color-border)]'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {loading && <p className="text-sm text-[var(--color-muted)]">Loading…</p>}

      {!loading && (!activities || activities.length === 0) && (
        <EmptyState
          title="No activities yet"
          description="Record a ride on the Android app and sync — it'll show up here."
        />
      )}

      <ul className="flex flex-col divide-y divide-[var(--color-border)] rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)]">
        {activities?.map((a) => (
          <li key={a.id} className="flex items-center justify-between px-4 py-3">
            <div>
              <p className="text-sm font-medium">{a.title || a.activity_type}</p>
              <p className="text-xs text-[var(--color-muted)]">{new Date(a.start_time).toLocaleDateString()}</p>
            </div>
            <div className="text-right text-xs text-[var(--color-muted)]">
              <p>{formatKm(a.distance_meters)}</p>
              <p>
                {formatDuration(a.moving_seconds)} · {a.average_speed_mps ? formatKmh(a.average_speed_mps) : '—'}
              </p>
            </div>
          </li>
        ))}
      </ul>

      <div className="flex justify-center gap-2">
        <button disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))} className="text-xs disabled:opacity-30">
          Prev
        </button>
        <button
          disabled={(activities?.length ?? 0) < PAGE_SIZE}
          onClick={() => setPage((p) => p + 1)}
          className="text-xs disabled:opacity-30"
        >
          Next
        </button>
      </div>
    </div>
  )
}
