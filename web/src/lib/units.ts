// Central unit-conversion + formatting helpers. Everything is stored in SI
// units (meters, meters/second, seconds) end to end — see docs/database.md
// — and converted to display units only here, at the UI boundary.

export function metersToKm(meters: number): number {
  return meters / 1000
}

export function mpsToKmh(mps: number): number {
  return mps * 3.6
}

export function formatKm(meters: number, fractionDigits = 1): string {
  return `${metersToKm(meters).toFixed(fractionDigits)} km`
}

export function formatKmh(mps: number, fractionDigits = 1): string {
  return `${mpsToKmh(mps).toFixed(fractionDigits)} km/h`
}

export function formatPace(mps: number): string {
  if (mps <= 0) return '—'
  const secPerKm = 1000 / mps
  const min = Math.floor(secPerKm / 60)
  const sec = Math.round(secPerKm % 60)
  return `${min}:${sec.toString().padStart(2, '0')} /km`
}

/** Walking/jogging read better as pace (min/km); cycling/motorcycling as speed (km/h) —
 *  mirrors the same convention on Android (RecordingScreen, ShareCard). */
export function formatSpeedOrPace(mps: number | null, activityType: string): string {
  if (mps == null || mps <= 0) return '—'
  return activityType === 'walking' || activityType === 'jogging' ? formatPace(mps) : formatKmh(mps)
}

export function formatDuration(totalSeconds: number): string {
  const h = Math.floor(totalSeconds / 3600)
  const m = Math.floor((totalSeconds % 3600) / 60)
  const s = Math.floor(totalSeconds % 60)
  if (h > 0) return `${h}h ${m}m`
  if (m > 0) return `${m}m ${s}s`
  return `${s}s`
}

export function formatElevation(meters: number): string {
  return `+${Math.round(meters)}m`
}

export function formatSteps(steps: number): string {
  return steps.toLocaleString('en-US')
}
