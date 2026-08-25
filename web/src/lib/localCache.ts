// Browser-side cache only — NOT the source of truth. Per spec section 4/9:
// the web app may cache Supabase data locally (IndexedDB via Dexie) for
// snappy repeat loads and offline browsing of already-fetched data, but the
// authoritative detailed record lives on the Android device, and the
// authoritative synced record lives in Supabase.
import Dexie, { type EntityTable } from 'dexie'
import type { ActivityRow, DailyStatsRow, MonthlyStatsRow, PersonalRecordRow } from '../types/database'

class TelemetryCacheDB extends Dexie {
  activities!: EntityTable<ActivityRow, 'id'>
  dailyStats!: EntityTable<DailyStatsRow, 'id'>
  monthlyStats!: EntityTable<MonthlyStatsRow, 'id'>
  personalRecords!: EntityTable<PersonalRecordRow, 'id'>

  constructor() {
    super('personalstrava-cache')
    this.version(1).stores({
      activities: 'id, user_id, activity_type, start_time',
      dailyStats: 'id, user_id, date',
      monthlyStats: 'id, user_id, month',
      personalRecords: 'id, user_id, record_key',
    })
  }
}

export const cacheDb = new TelemetryCacheDB()

export async function clearLocalCache() {
  await Promise.all([
    cacheDb.activities.clear(),
    cacheDb.dailyStats.clear(),
    cacheDb.monthlyStats.clear(),
    cacheDb.personalRecords.clear(),
  ])
}
