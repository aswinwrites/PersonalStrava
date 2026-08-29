import { useState } from 'react'
import { format, startOfMonth, startOfYear, subDays } from 'date-fns'
import { supabase } from '../lib/supabaseClient'
import { activitiesToCsv, downloadTextFile } from '../lib/csvExport'
import type { ActivityRow, ActivityType } from '../types/database'

const PERIODS = [
  { label: 'This week', start: () => format(subDays(new Date(), 7), 'yyyy-MM-dd') },
  { label: 'This month', start: () => format(startOfMonth(new Date()), 'yyyy-MM-dd') },
  { label: 'This year', start: () => format(startOfYear(new Date()), 'yyyy-MM-dd') },
  { label: 'All time', start: () => '1970-01-01' },
] as const

export function ExportPage() {
  const [busy, setBusy] = useState<string | null>(null)

  async function exportCsv(periodStart: string, label: string, activityType?: ActivityType) {
    setBusy(label)
    try {
      let query = supabase.from('activities').select('*').gte('start_time', periodStart).order('start_time')
      if (activityType) query = query.eq('activity_type', activityType)
      const { data, error } = await query
      if (error) throw error
      const csv = activitiesToCsv((data ?? []) as ActivityRow[])
      const suffix = activityType ?? 'activities'
      downloadTextFile(`${suffix}_${label.replace(/\s+/g, '_').toLowerCase()}.csv`, csv)
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <section>
        <h2 className="text-sm font-semibold tracking-tight">CSV export</h2>
        <p className="mt-1 text-xs text-[var(--color-muted)]">
          Activity summaries only — GPS points stay on your Android device. Per spec section 31, per-type CSVs
          (walking/jogging/cycling/motorcycling) plus daily/monthly stats CSVs are generated the same way.
        </p>
        <div className="mt-3 flex flex-wrap gap-2">
          {PERIODS.map((p) => (
            <button
              key={p.label}
              disabled={busy !== null}
              onClick={() => void exportCsv(p.start(), p.label)}
              className="rounded-full border border-[var(--color-border)] px-3 py-1.5 text-xs font-medium disabled:opacity-40"
            >
              {busy === p.label ? 'Exporting…' : p.label}
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-2xl border border-dashed border-[var(--color-border)] p-4">
        <h2 className="text-sm font-semibold tracking-tight">GPX / JSON / ZIP archives</h2>
        <p className="mt-1 text-xs text-[var(--color-muted)]">
          Phase 2: GPX export needs full-resolution route points, which live on the Android device — so the archive
          pipeline (export → checksum → verify → confirm → delete cloud detail, spec sections 31–35) is built as an
          Edge Function that the Android app also feeds via its own local GPX export. The client-side CSV export
          above already ships in Phase 1 because it only needs the already-synced `activities` summary rows.
        </p>
      </section>
    </div>
  )
}
