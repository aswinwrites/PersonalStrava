import { Suspense, lazy } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './features/auth/AuthProvider'
import { RequireAuth } from './features/auth/RequireAuth'
import { AuthCallback } from './features/auth/AuthCallback'
import { AppShell } from './components/AppShell'
import { HomePage } from './pages/HomePage'
import { ActivitiesPage } from './pages/ActivitiesPage'
import { ActivityDetailPage } from './pages/ActivityDetailPage'
import { AnalyticsPage } from './pages/AnalyticsPage'
import { ExportPage } from './pages/ExportPage'
import { SettingsPage } from './pages/SettingsPage'

// MapLibre GL is the single biggest dependency in the bundle — code-split it
// so signing in and checking today's steps doesn't pay for the map engine.
const MapPage = lazy(() => import('./pages/MapPage').then((m) => ({ default: m.MapPage })))

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/auth/callback" element={<AuthCallback />} />
          <Route
            element={
              <RequireAuth>
                <AppShell />
              </RequireAuth>
            }
          >
            <Route path="/" element={<HomePage />} />
            <Route path="/activities" element={<ActivitiesPage />} />
            <Route path="/activities/:id" element={<ActivityDetailPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route
              path="/map"
              element={
                <Suspense fallback={<div className="text-sm text-[var(--color-muted)]">Loading map…</div>}>
                  <MapPage />
                </Suspense>
              }
            />
            <Route path="/export" element={<ExportPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
