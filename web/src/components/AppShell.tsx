import { NavLink, Outlet } from 'react-router-dom'
import { Activity, BarChart3, Download, Home, Map as MapIcon, Settings as SettingsIcon, LogOut } from 'lucide-react'
import { useAuth } from '../features/auth/AuthProvider'
import { ThemeToggle } from './ThemeToggle'

const NAV_ITEMS = [
  { to: '/', label: 'Home', end: true, icon: Home },
  { to: '/activities', label: 'Activities', end: false, icon: Activity },
  { to: '/analytics', label: 'Analytics', end: false, icon: BarChart3 },
  { to: '/map', label: 'Map', end: false, icon: MapIcon },
  { to: '/export', label: 'Export', end: false, icon: Download },
  { to: '/settings', label: 'Settings', end: false, icon: SettingsIcon },
]

export function AppShell() {
  const { user, signOut } = useAuth()

  return (
    <div className="min-h-screen bg-[var(--color-paper)] text-[var(--color-ink)]">
      {/* Desktop / tablet: top bar with full nav. Hidden below sm. */}
      <header className="sticky top-0 z-20 hidden border-b border-[var(--color-border)] bg-[var(--color-paper)]/85 backdrop-blur sm:block">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="h-2 w-2 rounded-full bg-gradient-to-br from-[var(--color-cycling)] to-[var(--color-motorcycling)]" />
            <span className="text-sm font-semibold tracking-tight">Telemetry</span>
          </div>
          <nav className="flex gap-1">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-medium transition ${
                    isActive
                      ? 'bg-[var(--color-ink)] text-[var(--color-paper)]'
                      : 'text-[var(--color-muted)] hover:text-[var(--color-ink)]'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-3">
            <ThemeToggle />
            <button
              onClick={() => void signOut()}
              title={user?.email ?? ''}
              className="flex items-center gap-1.5 text-xs text-[var(--color-muted)] hover:text-[var(--color-ink)]"
            >
              <LogOut size={13} />
              Sign out
            </button>
          </div>
        </div>
      </header>

      {/* Mobile: compact top bar (brand + theme + sign out) ... */}
      <header className="sticky top-0 z-20 flex items-center justify-between border-b border-[var(--color-border)] bg-[var(--color-paper)]/90 px-4 py-3 backdrop-blur sm:hidden">
        <div className="flex items-center gap-2">
          <span className="h-2 w-2 rounded-full bg-gradient-to-br from-[var(--color-cycling)] to-[var(--color-motorcycling)]" />
          <span className="text-sm font-semibold tracking-tight">Telemetry</span>
        </div>
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <button
            onClick={() => void signOut()}
            aria-label="Sign out"
            className="flex h-8 w-8 items-center justify-center rounded-full text-[var(--color-muted)] hover:bg-[var(--color-border)]/50 hover:text-[var(--color-ink)]"
          >
            <LogOut size={16} />
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6 pb-24 sm:pb-6">
        <Outlet />
      </main>

      {/* ... and a fixed bottom tab bar for actual navigation — the pattern
          Strava/Apple Fitness use on mobile, and the natural fit for
          thumb reach compared to a horizontally-scrolling top nav. */}
      <nav className="fixed inset-x-0 bottom-0 z-20 border-t border-[var(--color-border)] bg-[var(--color-surface)]/95 backdrop-blur pb-[env(safe-area-inset-bottom)] sm:hidden">
        <div className="mx-auto flex max-w-5xl justify-between px-2 py-1.5">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `flex flex-1 flex-col items-center gap-0.5 rounded-xl py-1.5 text-[10px] font-medium transition ${
                    isActive ? 'text-[var(--color-ink)]' : 'text-[var(--color-muted)]'
                  }`
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon size={18} strokeWidth={isActive ? 2.4 : 2} />
                    {item.label}
                  </>
                )}
              </NavLink>
            )
          })}
        </div>
      </nav>
    </div>
  )
}
