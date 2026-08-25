import { describe, expect, it } from 'vitest'
import { formatDuration, formatKm, formatKmh, formatSteps, metersToKm, mpsToKmh } from './units'

describe('units', () => {
  it('converts meters to km', () => {
    expect(metersToKm(87400)).toBeCloseTo(87.4)
  })

  it('converts mps to km/h', () => {
    expect(mpsToKmh(7.36)).toBeCloseTo(26.5, 1)
  })

  it('formats km with one decimal by default', () => {
    expect(formatKm(87400)).toBe('87.4 km')
  })

  it('formats km/h', () => {
    expect(formatKmh(17)).toBe('61.2 km/h')
  })

  it('formats duration under a minute as seconds', () => {
    expect(formatDuration(45)).toBe('45s')
  })

  it('formats duration under an hour as minutes and seconds', () => {
    expect(formatDuration(125)).toBe('2m 5s')
  })

  it('formats duration over an hour as hours and minutes', () => {
    // 3h 18m == 11880s
    expect(formatDuration(11880)).toBe('3h 18m')
  })

  it('formats step counts with thousands separators', () => {
    expect(formatSteps(8421)).toBe('8,421')
  })
})
