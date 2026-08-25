import { describe, expect, it } from 'vitest'
import { decodePolyline } from './polyline'

describe('decodePolyline', () => {
  it('decodes the canonical Google polyline example', () => {
    // From Google's polyline algorithm documentation: encodes
    // (38.5,-120.2) (40.7,-120.95) (43.252,-126.453)
    const encoded = '_p~iF~ps|U_ulLnnqC_mqNvxq`@'
    const points = decodePolyline(encoded)
    expect(points).toHaveLength(3)
    expect(points[0][1]).toBeCloseTo(38.5, 4) // lat
    expect(points[0][0]).toBeCloseTo(-120.2, 4) // lng
    expect(points[2][1]).toBeCloseTo(43.252, 3)
    expect(points[2][0]).toBeCloseTo(-126.453, 3)
  })

  it('returns an empty array for an empty string', () => {
    expect(decodePolyline('')).toEqual([])
  })
})
