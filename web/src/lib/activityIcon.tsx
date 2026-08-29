import { Footprints, Activity, Bike, Zap } from 'lucide-react'
import type { ActivityType } from '../types/database'

export const ACTIVITY_ICON = {
  walking: Footprints,
  jogging: Activity,
  cycling: Bike,
  motorcycling: Zap,
} as const satisfies Record<ActivityType, typeof Footprints>

export const ACTIVITY_LABEL: Record<ActivityType, string> = {
  walking: 'Walking',
  jogging: 'Jogging',
  cycling: 'Cycling',
  motorcycling: 'Motorcycling',
}
