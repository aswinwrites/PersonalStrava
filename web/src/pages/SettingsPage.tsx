import { useEffect, useState } from 'react'
import { supabase } from '../lib/supabaseClient'
import { useAuth } from '../features/auth/AuthProvider'
import type { ProfileRow } from '../types/database'

export function SettingsPage() {
  const { user } = useAuth()
  const [profile, setProfile] = useState<ProfileRow | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!user) return
    supabase
      .from('profiles')
      .select('*')
      .eq('id', user.id)
      .single()
      .then(({ data }) => setProfile(data as ProfileRow | null))
  }, [user])

  async function toggle(field: 'weekly_report_enabled' | 'monthly_report_enabled') {
    if (!profile) return
    setSaving(true)
    const next = { ...profile, [field]: !profile[field] }
    setProfile(next)
    await supabase
      .from('profiles')
      .update({ [field]: next[field] } as Partial<ProfileRow>)
      .eq('id', profile.id)
    setSaving(false)
  }

  return (
    <div className="flex flex-col gap-6">
      <section>
        <h2 className="text-sm font-semibold tracking-tight">Account</h2>
        <p className="mt-1 text-sm text-[var(--color-muted)]">{user?.email}</p>
      </section>

      <section>
        <h2 className="text-sm font-semibold tracking-tight">Email reports</h2>
        <p className="mt-1 text-xs text-[var(--color-muted)]">
          Deterministic, no AI-generated content (spec section 36). Sent by the `send-report` Edge Function via
          Resend on a schedule you configure in Supabase (see docs/deployment.md).
        </p>
        <div className="mt-3 flex flex-col gap-2">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={profile?.weekly_report_enabled ?? false}
              disabled={!profile || saving}
              onChange={() => void toggle('weekly_report_enabled')}
            />
            Weekly report
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={profile?.monthly_report_enabled ?? false}
              disabled={!profile || saving}
              onChange={() => void toggle('monthly_report_enabled')}
            />
            Monthly report
          </label>
        </div>
      </section>

      <section>
        <h2 className="text-sm font-semibold tracking-tight">Cloud data management</h2>
        <p className="mt-1 text-xs text-[var(--color-muted)]">
          Archive manager (export → verify → delete detailed cloud data, keeping aggregates and personal records —
          spec sections 34–35) lands in Phase 2 once the export archive pipeline exists.
        </p>
      </section>
    </div>
  )
}
