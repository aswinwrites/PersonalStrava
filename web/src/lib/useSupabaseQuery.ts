import { useEffect, useState } from 'react'

/**
 * Minimal fetch-on-mount hook. Deliberately not a full query-caching library
 * (no react-query dependency) for Phase 1 — swap this out if/when the data
 * surface grows enough to need request de-duplication and background
 * refetch. `deps` re-runs the fetch when any dependency changes.
 */
export function useSupabaseQuery<T>(
  fetcher: () => PromiseLike<{ data: T | null; error: { message: string } | null }>,
  deps: unknown[],
) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    Promise.resolve(fetcher())
      .then(({ data, error }) => {
        if (cancelled) return
        if (error) setError(error.message)
        else setData(data)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : String(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return { data, error, loading }
}
