interface MovementRingProps {
  walkingSeconds: number
  cyclingSeconds: number
  motorcyclingSeconds: number
  steps: number
  size?: number
}

/**
 * Apple-Fitness-style concentric activity ring, sized to today's active
 * seconds by type. Purely a visual summary — the numbers underneath are the
 * source of truth, this is the "at a glance" layer the spec's "Strava ×
 * Apple Fitness" brief (section 37) calls for.
 */
export function MovementRing({ walkingSeconds, cyclingSeconds, motorcyclingSeconds, steps, size = 148 }: MovementRingProps) {
  const total = walkingSeconds + cyclingSeconds + motorcyclingSeconds
  const radius = size / 2 - 10
  const circumference = 2 * Math.PI * radius
  const stroke = 10

  const segments = [
    { seconds: walkingSeconds, color: 'var(--color-walking)' },
    { seconds: cyclingSeconds, color: 'var(--color-cycling)' },
    { seconds: motorcyclingSeconds, color: 'var(--color-motorcycling)' },
  ]

  let offsetAccumulator = 0

  return (
    <div className="relative flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none" stroke="var(--color-border)" strokeWidth={stroke} />
        {total > 0 &&
          segments.map((segment, i) => {
            if (segment.seconds === 0) return null
            const fraction = segment.seconds / total
            const dash = fraction * circumference
            const gap = circumference - dash
            const el = (
              <circle
                key={i}
                cx={size / 2}
                cy={size / 2}
                r={radius}
                fill="none"
                stroke={segment.color}
                strokeWidth={stroke}
                strokeDasharray={`${dash} ${gap}`}
                strokeDashoffset={-offsetAccumulator}
                strokeLinecap="round"
                className="transition-all duration-700 ease-out"
              />
            )
            offsetAccumulator += dash
            return el
          })}
      </svg>
      <div className="absolute flex flex-col items-center">
        <span className="text-2xl font-semibold tracking-tight font-mono-stat">{steps.toLocaleString('en-US')}</span>
        <span className="text-[10px] uppercase tracking-wide text-[var(--color-muted)]">steps today</span>
      </div>
    </div>
  )
}
