import { useEffect, useRef, useState } from 'react'
import { Map as MapLibreMap, LngLatBounds } from 'maplibre-gl'
import { supabase } from '../lib/supabaseClient'
import { useSupabaseQuery } from '../lib/useSupabaseQuery'
import { decodePolyline } from '../lib/polyline'
import type { ActivityType } from '../types/database'

interface RouteRow {
  id: string
  activity_type: ActivityType
  route_polyline: string | null
}

const ACTIVITY_COLORS: Record<ActivityType, string> = {
  walking: '#2f9e6f',
  cycling: '#ff6a3d',
  motorcycling: '#3d6bff',
}

// Free, no-API-key vector basemap. Swap for a self-hosted style if you want
// full control / offline tiles later — see docs/web.md.
const MAP_STYLE = 'https://demotiles.maplibre.org/style.json'

export function MapPage() {
  const [filter, setFilter] = useState<ActivityType | 'all'>('all')
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<MapLibreMap | null>(null)

  const { data: activities } = useSupabaseQuery<RouteRow[]>(() => {
    let query = supabase.from('activities').select('id, activity_type, route_polyline').not('route_polyline', 'is', null)
    if (filter !== 'all') query = query.eq('activity_type', filter)
    return query.limit(500)
  }, [filter])

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return
    mapRef.current = new MapLibreMap({
      container: containerRef.current,
      style: MAP_STYLE,
      center: [0, 20],
      zoom: 1.5,
    })
    return () => {
      mapRef.current?.remove()
      mapRef.current = null
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !activities) return

    const applyRoutes = () => {
      // Remove previously drawn route layers/sources before redrawing.
      const style = map.getStyle()
      style?.layers?.forEach((layer: { id: string }) => {
        if (layer.id.startsWith('route-')) map.removeLayer(layer.id)
      })
      Object.keys(style?.sources ?? {}).forEach((id) => {
        if (id.startsWith('route-')) map.removeSource(id)
      })

      const bounds = new LngLatBounds()
      let hasAny = false

      for (const activity of activities) {
        if (!activity.route_polyline) continue
        const coords = decodePolyline(activity.route_polyline)
        if (coords.length < 2) continue
        hasAny = true
        coords.forEach((c) => bounds.extend(c))

        const sourceId = `route-${activity.id}`
        map.addSource(sourceId, {
          type: 'geojson',
          data: { type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: coords } },
        })
        map.addLayer({
          id: sourceId,
          type: 'line',
          source: sourceId,
          paint: {
            'line-color': ACTIVITY_COLORS[activity.activity_type],
            'line-width': 2,
            'line-opacity': 0.85,
          },
        })
      }

      if (hasAny) map.fitBounds(bounds, { padding: 40, maxZoom: 14, duration: 0 })
    }

    if (map.isStyleLoaded()) applyRoutes()
    else map.once('load', applyRoutes)
  }, [activities])

  return (
    <div className="flex flex-col gap-3">
      <div className="flex gap-1">
        {(['all', 'walking', 'cycling', 'motorcycling'] as const).map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`rounded-full px-3 py-1.5 text-xs font-medium capitalize ${
              filter === f ? 'bg-[var(--color-ink)] text-[var(--color-paper)]' : 'border border-[var(--color-border)]'
            }`}
          >
            {f}
          </button>
        ))}
      </div>
      <div ref={containerRef} className="h-[70vh] w-full overflow-hidden rounded-2xl border border-[var(--color-border)]" />
      <p className="text-xs text-[var(--color-muted)]">
        Routes are the simplified `route_polyline` synced from Android — full-resolution GPS points never leave the
        device. Heatmap mode is a documented later enhancement (spec section 28).
      </p>
    </div>
  )
}
