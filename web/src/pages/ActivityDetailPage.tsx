import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Plus, X } from 'lucide-react'
import { supabase } from '../lib/supabaseClient'
import { useAuth } from '../features/auth/AuthProvider'
import { formatDuration, formatElevation, formatKm, formatKmh } from '../lib/units'
import { ACTIVITY_ICON, ACTIVITY_LABEL } from '../lib/activityIcon'
import type { ActivityPhotoRow, ActivityRow } from '../types/database'

const PHOTOS_BUCKET = 'activity-photos'

/**
 * The "just for my memory" surface (spec follow-up ask): title/notes edit
 * plus a photo gallery, for one already-synced activity. `activity_photos`
 * is private (per supabase/migrations/0006_photos_and_avatars.sql, unlike
 * the public `avatars` bucket), so every photo shown here is a short-lived
 * signed URL fetched on load rather than a plain public URL — nothing here
 * is ever meant to be link-shareable, only the end-of-ride share card
 * (rendered on-device) is.
 */
export function ActivityDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()

  const [activity, setActivity] = useState<ActivityRow | null>(null)
  const [photos, setPhotos] = useState<Array<ActivityPhotoRow & { url: string | null }>>([])
  const [loading, setLoading] = useState(true)
  const [titleDraft, setTitleDraft] = useState('')
  const [notesDraft, setNotesDraft] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    if (!id || !user) return
    setLoading(true)
    setError(null)

    const [{ data: activityData, error: activityError }, { data: photoRows, error: photoError }] = await Promise.all([
      supabase.from('activities').select('*').eq('id', id).single(),
      supabase.from('activity_photos').select('*').eq('activity_id', id).order('position', { ascending: true }),
    ])

    if (activityError) {
      setError(activityError.message)
      setLoading(false)
      return
    }

    const rows = (photoRows ?? []) as ActivityPhotoRow[]
    const withUrls = await Promise.all(
      rows.map(async (row) => {
        const { data: signed } = await supabase.storage.from(PHOTOS_BUCKET).createSignedUrl(row.storage_path, 3600)
        return { ...row, url: signed?.signedUrl ?? null }
      }),
    )

    const a = activityData as ActivityRow
    setActivity(a)
    setTitleDraft(a.title ?? '')
    setNotesDraft(a.notes ?? '')
    setPhotos(withUrls)
    if (photoError) setError(photoError.message)
    setLoading(false)
  }, [id, user])

  useEffect(() => {
    void load()
  }, [load])

  async function save() {
    if (!activity) return
    setSaving(true)
    const patch = { title: titleDraft.trim() || null, notes: notesDraft.trim() || null }
    const { error: updateError } = await supabase.from('activities').update(patch).eq('id', activity.id)
    setSaving(false)
    if (updateError) {
      setError(updateError.message)
      return
    }
    setActivity({ ...activity, ...patch })
    setSaved(true)
    setTimeout(() => setSaved(false), 1500)
  }

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0 || !activity || !user) return
    setUploading(true)
    setError(null)

    const startPosition = photos.length
    let position = startPosition
    for (const file of Array.from(files)) {
      const photoId = crypto.randomUUID()
      const storagePath = `${user.id}/${activity.id}/${photoId}.jpg`
      const { error: uploadError } = await supabase.storage.from(PHOTOS_BUCKET).upload(storagePath, file, { upsert: true })
      if (uploadError) {
        setError(uploadError.message)
        continue
      }
      const { error: insertError } = await supabase.from('activity_photos').insert({
        id: photoId,
        user_id: user.id,
        activity_id: activity.id,
        storage_path: storagePath,
        caption: null,
        position: position++,
      })
      if (insertError) setError(insertError.message)
    }

    setUploading(false)
    if (fileInputRef.current) fileInputRef.current.value = ''
    void load()
  }

  async function deletePhoto(photo: ActivityPhotoRow) {
    await supabase.storage.from(PHOTOS_BUCKET).remove([photo.storage_path])
    await supabase.from('activity_photos').delete().eq('id', photo.id)
    setPhotos((prev) => prev.filter((p) => p.id !== photo.id))
  }

  if (loading) return <p className="text-sm text-[var(--color-muted)]">Loading…</p>
  if (!activity) return <p className="text-sm text-[var(--color-muted)]">{error ?? 'Activity not found.'}</p>

  const Icon = ACTIVITY_ICON[activity.activity_type]

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center gap-3">
        <button
          onClick={() => navigate('/activities')}
          className="flex h-8 w-8 items-center justify-center rounded-full border border-[var(--color-border)] text-[var(--color-muted)] hover:text-[var(--color-ink)]"
        >
          <ArrowLeft size={15} />
        </button>
        <span
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full"
          style={{
            backgroundColor: `color-mix(in srgb, var(--color-${activity.activity_type}) 15%, transparent)`,
            color: `var(--color-${activity.activity_type})`,
          }}
        >
          <Icon size={16} />
        </span>
        <div>
          <p className="text-sm font-semibold">{activity.title || ACTIVITY_LABEL[activity.activity_type]}</p>
          <p className="text-xs text-[var(--color-muted)]">{new Date(activity.start_time).toLocaleString()}</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Distance" value={formatKm(activity.distance_meters)} />
        <Stat label="Time" value={formatDuration(activity.moving_seconds)} />
        <Stat
          label={activity.activity_type === 'walking' || activity.activity_type === 'jogging' ? 'Avg pace' : 'Avg speed'}
          value={activity.moving_average_speed_mps ? formatKmh(activity.moving_average_speed_mps) : '—'}
        />
        <Stat label="Elevation" value={formatElevation(activity.elevation_gain_meters)} />
      </div>

      <section className="flex flex-col gap-3 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <h2 className="text-sm font-semibold tracking-tight">Title & notes</h2>
        <input
          value={titleDraft}
          onChange={(e) => setTitleDraft(e.target.value)}
          placeholder={ACTIVITY_LABEL[activity.activity_type]}
          className="rounded-lg border border-[var(--color-border)] bg-transparent px-3 py-2 text-sm outline-none focus:border-[var(--color-ink)]"
        />
        <textarea
          value={notesDraft}
          onChange={(e) => setNotesDraft(e.target.value)}
          placeholder="How'd it go? (just for you — never shared)"
          rows={4}
          className="resize-none rounded-lg border border-[var(--color-border)] bg-transparent px-3 py-2 text-sm outline-none focus:border-[var(--color-ink)]"
        />
        <button
          onClick={() => void save()}
          disabled={saving}
          className="self-start rounded-full bg-[var(--color-ink)] px-4 py-1.5 text-xs font-medium text-[var(--color-paper)] disabled:opacity-50"
        >
          {saved ? 'Saved' : saving ? 'Saving…' : 'Save'}
        </button>
      </section>

      <section className="flex flex-col gap-3 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
        <h2 className="text-sm font-semibold tracking-tight">Photos</h2>
        <div className="flex flex-wrap gap-3">
          {photos.map((photo) => (
            <div key={photo.id} className="group relative h-24 w-24 shrink-0 overflow-hidden rounded-lg border border-[var(--color-border)]">
              {photo.url ? (
                <img src={photo.url} alt="" className="h-full w-full object-cover" />
              ) : (
                <div className="h-full w-full bg-[var(--color-border)]/40" />
              )}
              <button
                onClick={() => void deletePhoto(photo)}
                className="absolute right-1 top-1 flex h-5 w-5 items-center justify-center rounded-full bg-black/60 text-white opacity-0 transition group-hover:opacity-100"
              >
                <X size={12} />
              </button>
            </div>
          ))}
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="flex h-24 w-24 shrink-0 items-center justify-center rounded-lg border border-dashed border-[var(--color-border)] text-[var(--color-muted)] hover:text-[var(--color-ink)] disabled:opacity-50"
          >
            <Plus size={20} />
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            multiple
            className="hidden"
            onChange={(e) => void handleFiles(e.target.files)}
          />
        </div>
        {uploading && <p className="text-xs text-[var(--color-muted)]">Uploading…</p>}
      </section>

      {error && <p className="text-xs text-red-500">{error}</p>}
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-3">
      <p className="text-lg font-semibold tabular-nums">{value}</p>
      <p className="text-[11px] uppercase tracking-wide text-[var(--color-muted)]">{label}</p>
    </div>
  )
}
