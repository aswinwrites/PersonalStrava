import { createClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY

if (!url || !anonKey) {
  // Fail loud in dev rather than silently making unauthenticated requests
  // that 401 mysteriously later.
  // eslint-disable-next-line no-console
  console.error(
    'Missing VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY. Copy .env.example to web/.env.local and fill in your Supabase project values.',
  )
}

// Not parameterized with the generated `Database` type yet — see
// src/types/database.ts for the hand-written row shapes used to cast
// query results at each call site. Once the Supabase project exists, run
// `supabase gen types typescript` and wire it back in as
// `createClient<Database>(...)` for end-to-end inference.
export const supabase = createClient(url ?? '', anonKey ?? '', {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: true,
  },
})
