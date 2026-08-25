import type { ActivityRow } from '../types/database'

const ACTIVITY_COLUMNS: Array<keyof ActivityRow> = [
  'id',
  'activity_type',
  'start_time',
  'end_time',
  'elapsed_seconds',
  'moving_seconds',
  'distance_meters',
  'elevation_gain_meters',
  'elevation_loss_meters',
  'average_speed_mps',
  'moving_average_speed_mps',
  'max_speed_mps',
  'title',
  'notes',
]

function csvEscape(value: unknown): string {
  if (value === null || value === undefined) return ''
  const str = String(value)
  if (/[",\n]/.test(str)) return `"${str.replace(/"/g, '""')}"`
  return str
}

export function activitiesToCsv(activities: ActivityRow[]): string {
  const header = ACTIVITY_COLUMNS.join(',')
  const rows = activities.map((a) => ACTIVITY_COLUMNS.map((col) => csvEscape(a[col])).join(','))
  return [header, ...rows].join('\n')
}

export function downloadTextFile(filename: string, content: string, mimeType = 'text/csv') {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
