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
