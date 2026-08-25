import { Activity } from 'lucide-react'
import { useAuth } from './AuthProvider'

export function SignInScreen() {
  const { signInWithGoogle } = useAuth()

  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center gap-6 overflow-hidden bg-[var(--color-paper)] px-6 text-center">
      <div
        className="pointer-events-none absolute inset-0"
        style={{
          backgroundImage:
            'radial-gradient(circle at 50% 0%, color-mix(in srgb, var(--color-cycling) 10%, transparent), transparent 60%)',
        }}
      />
      <div className="relative flex flex-col items-center gap-4">
        <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-br from-[var(--color-cycling)] to-[var(--color-motorcycling)] text-white shadow-lg">
          <Activity size={22} />
        </div>
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Telemetry</h1>
          <p className="mt-2 max-w-sm text-sm text-[var(--color-muted)]">
            A private, single-user activity tracker. Sign in with the same Google account you use on
            the Android app to see your synced history.
          </p>
        </div>
        <button
          onClick={() => void signInWithGoogle()}
          className="flex items-center gap-2 rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] px-5 py-2.5 text-sm font-medium shadow-sm transition hover:-translate-y-0.5 hover:shadow-md"
        >
          Continue with Google
        </button>
      </div>
    </div>
  )
}
