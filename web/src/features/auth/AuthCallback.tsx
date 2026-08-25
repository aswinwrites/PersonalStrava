import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { supabase } from '../../lib/supabaseClient'

// Supabase JS handles the OAuth code exchange automatically via
// detectSessionInUrl (see supabaseClient.ts). This route just waits for the
// session to land, then routes home.
export function AuthCallback() {
  const navigate = useNavigate()

  useEffect(() => {
    supabase.auth.getSession().then(() => navigate('/', { replace: true }))
  }, [navigate])

  return (
    <div className="flex min-h-screen items-center justify-center text-[var(--color-muted)]">
      Signing in…
    </div>
  )
}
