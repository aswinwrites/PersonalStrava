// Hand-written types mirroring supabase/migrations/0001_init_schema.sql.
// Once the Supabase project exists, regenerate with:
//   supabase gen types typescript --project-id <ref> > src/types/database.ts
// and this file becomes generated rather than hand-maintained.

export type ActivityType = 'walking' | 'cycling' | 'motorcycling'
export type SyncStatus = 'local' | 'pending_sync' | 'syncing' | 'synced' | 'sync_failed' | 'archived'
export type ExportStatus = 'generated' | 'verified' | 'detail_deleted'

export interface ActivityRow {
  id: string
  user_id: string
  activity_type: ActivityType
  start_time: string
  end_time: string
  elapsed_seconds: number
  moving_seconds: number
  distance_meters: number
  elevation_gain_meters: number
  elevation_loss_meters: number
  average_speed_mps: number | null
  moving_average_speed_mps: number | null
  max_speed_mps: number | null
  start_latitude: number | null
  start_longitude: number | null
  end_latitude: number | null
  end_longitude: number | null
  route_polyline: string | null
  title: string | null
  notes: string | null
  sync_status: SyncStatus
  created_at: string
  updated_at: string
}

export interface DailyStatsRow {
  id: string
  user_id: string
  date: string
  steps: number
  walking_distance_meters: number
  cycling_distance_meters: number
  motorcycling_distance_meters: number
  walking_seconds: number
  cycling_seconds: number
  motorcycling_seconds: number
  elevation_gain_meters: number
  activity_count: number
  created_at: string
  updated_at: string
}

export interface MonthlyStatsRow {
  id: string
  user_id: string
  month: string
  steps: number
  walking_distance_meters: number
  cycling_distance_meters: number
  motorcycling_distance_meters: number
  walking_seconds: number
  cycling_seconds: number
  motorcycling_seconds: number
  elevation_gain_meters: number
  activity_count: number
  created_at: string
  updated_at: string
}

export interface PersonalRecordRow {
  id: string
  user_id: string
  record_key: string
  activity_type: ActivityType | null
  value_numeric: number
  value_unit: string
  activity_id: string | null
  achieved_on: string | null
  created_at: string
  updated_at: string
}

export interface ProfileRow {
  id: string
  display_name: string | null
  avatar_url: string | null
  weekly_report_enabled: boolean
  monthly_report_enabled: boolean
  report_email: string | null
  created_at: string
  updated_at: string
}

export interface ExportMetadataRow {
  id: string
  user_id: string
  period_label: string
  period_start: string
  period_end: string
  activity_count: number
  gps_point_count: number
  steps: number
  distance_meters: number
  schema_version: number
  checksum: string
  storage_path: string | null
  status: ExportStatus
  verified_at: string | null
  detail_deleted_at: string | null
  exported_at: string
  created_at: string
}

// Minimal Supabase Database generic shape — enough for the typed client to
// resolve table row types without a full generated codegen file yet.
export interface Database {
  public: {
    Tables: {
      activities: { Row: ActivityRow; Insert: Partial<ActivityRow> & { id: string; user_id: string; activity_type: ActivityType }; Update: Partial<ActivityRow> }
      daily_stats: { Row: DailyStatsRow; Insert: Partial<DailyStatsRow>; Update: Partial<DailyStatsRow> }
      monthly_stats: { Row: MonthlyStatsRow; Insert: Partial<MonthlyStatsRow>; Update: Partial<MonthlyStatsRow> }
      personal_records: { Row: PersonalRecordRow; Insert: Partial<PersonalRecordRow>; Update: Partial<PersonalRecordRow> }
      profiles: { Row: ProfileRow; Insert: Partial<ProfileRow>; Update: Partial<ProfileRow> }
      export_metadata: { Row: ExportMetadataRow; Insert: Partial<ExportMetadataRow>; Update: Partial<ExportMetadataRow> }
    }
    Functions: {
      get_period_totals: {
        Args: { period_start: string; period_end: string }
        Returns: PeriodTotals[]
      }
      get_lifetime_totals: {
        Args: Record<string, never>
        Returns: LifetimeTotals[]
      }
    }
  }
}

export interface PeriodTotals {
  steps: number
  walking_distance_meters: number
  cycling_distance_meters: number
  motorcycling_distance_meters: number
  walking_seconds: number
  cycling_seconds: number
  motorcycling_seconds: number
  elevation_gain_meters: number
  activity_count: number
}

export interface LifetimeTotals {
  walking_distance_meters: number
  cycling_distance_meters: number
  motorcycling_distance_meters: number
  total_distance_meters: number
  steps: number
  activity_count: number
}
