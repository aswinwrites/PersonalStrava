import type { ReactNode } from 'react'
import { useAuth } from './AuthProvider'
import { SignInScreen } from './SignInScreen'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { session, loading } = useAuth()

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center text-[var(--color-muted)]">
        Loading…
      </div>
    )
  }

  if (!session) {
    return <SignInScreen />
  }

  return <>{children}</>
}
