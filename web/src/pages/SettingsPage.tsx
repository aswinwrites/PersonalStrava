import { useEffect, useRef, useState } from 'react'
import { supabase } from '../lib/supabaseClient'
import { useAuth } from '../features/auth/AuthProvider'
import type { ProfileRow } from '../types/database'

export function SettingsPage() {
  const { user } = useAuth()
  const [profile, setProfile] = useState<ProfileRow | null>(null)
  const [saving, setSaving] = useState(false)
  const [nameDraft, setNameDraft] = useState('')
  const [nameSaved, setNameSaved] = useState(false)
  const [uploadingAvatar, setUploadingAvatar] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const avatarInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!user) return
    supabase
      .from('profiles')
      .select('*')
      .eq('id', user.id)
      .single()
      .then(({ data }) => {
        const row = data as ProfileRow | null
        setProfile(row)
        setNameDraft(row?.display_name ?? '')
      })
  }, [user])

  async function saveName() {
    if (!profile) return
    setSaving(true)
    setProfileError(null)
    const displayName = nameDraft.trim() || null
    const { error } = await supabase.from('profiles').update({ display_name: displayName }).eq('id', profile.id)
    setSaving(false)
    if (error) {
      setProfileError(error.message)
      return
    }
    setProfile({ ...profile, display_name: displayName })
    setNameSaved(true)
    setTimeout(() => setNameSaved(false), 1500)
  }

  // avatars is the public-read bucket (supabase/migrations/0006_photos_and_avatars.sql) — a
  // plain public URL works here, unlike the private activity-photos bucket used for ride photos.
  async function uploadAvatar(file: File) {
    if (!profile) return
    setUploadingAvatar(true)
    setProfileError(null)
    const path = `${profile.id}/avatar.jpg`
    const { error: uploadError } = await supabase.storage.from('avatars').upload(path, file, { upsert: true })
    if (uploadError) {
      setProfileError(uploadError.message)
      setUploadingAvatar(false)
      return
    }
    const { data: publicUrlData } = supabase.storage.from('avatars').getPublicUrl(path)
    const avatarUrl = `${publicUrlData.publicUrl}?t=${Date.now()}`
    const { error: updateError } = await supabase.from('profiles').update({ avatar_url: avatarUrl }).eq('id', profile.id)
    setUploadingAvatar(false)
    if (updateError) {
      setProfileError(updateError.message)
      return
    }
    setProfile({ ...profile, avatar_url: avatarUrl })
    if (avatarInputRef.current) avatarInputRef.current.value = ''
  }

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

        <div className="mt-4 flex items-center gap-4">
          <div className="relative h-16 w-16 shrink-0">
            {profile?.avatar_url ? (
              <img src={profile.avatar_url} alt="" className="h-16 w-16 rounded-full object-cover" />
            ) : (
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-[var(--color-border)]/40 text-sm font-medium text-[var(--color-muted)]">
                {(profile?.display_name || user?.email || '?').charAt(0).toUpperCase()}
              </div>
            )}
            {uploadingAvatar && (
              <div className="absolute inset-0 flex items-center justify-center rounded-full bg-black/40 text-[10px] text-white">…</div>
            )}
            <button
              onClick={() => avatarInputRef.current?.click()}
              disabled={uploadingAvatar}
              className="absolute -bottom-1 -right-1 flex h-6 w-6 items-center justify-center rounded-full bg-[var(--color-ink)] text-[10px] text-[var(--color-paper)] disabled:opacity-50"
            >
              ✎
            </button>
            <input
              ref={avatarInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) void uploadAvatar(file)
              }}
            />
          </div>

          <div className="flex flex-1 items-center gap-2">
            <input
              value={nameDraft}
              onChange={(e) => setNameDraft(e.target.value)}
              placeholder="Your name"
              className="min-w-0 flex-1 rounded-lg border border-[var(--color-border)] bg-transparent px-3 py-1.5 text-sm outline-none focus:border-[var(--color-ink)]"
            />
            <button
              onClick={() => void saveName()}
              disabled={saving || nameDraft.trim() === (profile?.display_name ?? '')}
              className="shrink-0 rounded-full bg-[var(--color-ink)] px-3 py-1.5 text-xs font-medium text-[var(--color-paper)] disabled:opacity-40"
            >
              {nameSaved ? 'Saved' : saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        </div>
        {profileError && <p className="mt-2 text-xs text-red-500">{profileError}</p>}
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
