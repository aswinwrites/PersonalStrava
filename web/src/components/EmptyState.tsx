import type { LucideIcon } from 'lucide-react'
import { Inbox } from 'lucide-react'

export function EmptyState({ title, description, icon: Icon = Inbox }: { title: string; description: string; icon?: LucideIcon }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-2xl border border-dashed border-[var(--color-border)] px-6 py-16 text-center">
      <div className="mb-1 flex h-10 w-10 items-center justify-center rounded-full bg-[var(--color-border)]/40 text-[var(--color-muted)]">
        <Icon size={18} />
      </div>
      <h3 className="text-base font-medium">{title}</h3>
      <p className="max-w-sm text-sm text-[var(--color-muted)]">{description}</p>
    </div>
  )
}
