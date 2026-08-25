import { useAuth } from './AuthProvider'

export function SignInScreen() {
  const { signInWithGoogle } = useAuth()

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 bg-[var(--color-paper)] px-6 text-center">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">PersonalStrava</h1>
        <p className="mt-2 max-w-sm text-sm text-[var(--color-muted)]">
          A private, single-user activity tracker. Sign in with the same Google account you use on
          the Android app to see your synced history.
        </p>
      </div>
      <button
        onClick={() => void signInWithGoogle()}
        className="flex items-center gap-2 rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] px-5 py-2.5 text-sm font-medium shadow-sm transition hover:shadow-md"
      >
        Continue with Google
      </button>
    </div>
  )
}
