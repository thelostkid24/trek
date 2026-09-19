import type { ButtonHTMLAttributes, ReactNode } from 'react'
import type { DepartureStatus } from '../../api/catalog.ts'
import { messageFor } from '../../auth/errorMessages.ts'

export function Panel({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-2xl border border-stone-200 bg-white p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-display text-lg font-semibold text-stone-900">{title}</h2>
        {action}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  )
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { tone?: 'primary' | 'secondary' | 'danger' }

export function Button({ tone = 'primary', className = '', type = 'button', ...button }: ButtonProps) {
  const tones = {
    primary: 'bg-brand-900 text-white hover:bg-brand-800',
    secondary: 'bg-white text-stone-800 ring-1 ring-stone-300 hover:ring-brand-400',
    danger: 'bg-laterite-600 text-white hover:bg-laterite-500',
  }
  return (
    <button
      {...button}
      type={type}
      className={`rounded-full px-4 py-1.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${tones[tone]} ${className}`}
    />
  )
}

const STATUS_TONE: Record<DepartureStatus, string> = {
  DRAFT: 'bg-stone-100 text-stone-600',
  PUBLISHED: 'bg-brand-100 text-brand-800',
  CANCELLED: 'bg-laterite-100 text-laterite-600',
  EXPIRED: 'bg-stone-100 text-stone-500',
  COMPLETED: 'bg-amber-100 text-amber-800',
}

export function StatusPill({ status }: { status: DepartureStatus }) {
  return (
    <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_TONE[status]}`}>
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  )
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  return (
    <p role="alert" className="rounded-lg bg-laterite-100 px-3 py-2 text-sm text-laterite-600">
      {messageFor(error)}
    </p>
  )
}

export function Loading() {
  return <div className="h-32 animate-pulse rounded-xl bg-stone-100" aria-busy="true" aria-label="Loading" />
}
