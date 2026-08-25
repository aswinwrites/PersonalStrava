import { useEffect, useRef, useState } from 'react'

/**
 * Animates a number from its previous value to `target` over `durationMs`,
 * eased out. Purely cosmetic — the "cool" factor on the Home stat tiles —
 * but cheap: one rAF loop per mounted tile, cancelled on unmount/re-target.
 */
export function useCountUp(target: number | null, durationMs = 700): number | null {
  const [value, setValue] = useState<number | null>(target)
  const fromRef = useRef(0)
  const frameRef = useRef<number | null>(null)

  useEffect(() => {
    if (target === null) return
    const from = fromRef.current
    const delta = target - from
    if (delta === 0) {
      setValue(target)
      return
    }
    const start = performance.now()

    const tick = (now: number) => {
      const elapsed = now - start
      const progress = Math.min(1, elapsed / durationMs)
      const eased = 1 - Math.pow(1 - progress, 3) // ease-out cubic
      const current = from + delta * eased
      setValue(current)
      if (progress < 1) {
        frameRef.current = requestAnimationFrame(tick)
      } else {
        fromRef.current = target
      }
    }

    frameRef.current = requestAnimationFrame(tick)
    return () => {
      if (frameRef.current !== null) cancelAnimationFrame(frameRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target, durationMs])

  return value
}
