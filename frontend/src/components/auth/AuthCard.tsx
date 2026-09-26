import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/** Shared frame for the sign-in and sign-up screens. Mobile-first: full width, centred from sm up. */
export function AuthCard({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}) {
  return (
    // Same paper, type and field styling as the landing page and account area.
    <div className="auth-area min-h-[calc(100dvh-4rem)] bg-paper-50 font-plex text-ink-900">
      <section className="mx-auto flex w-full max-w-md flex-col px-4 py-10 sm:py-16">
        <Link to="/" className="self-center font-serif text-[1.6rem] tracking-tight text-ink-900">
          The Empty Valley
        </Link>
        <div className="mt-6 rounded-(--card-radius) border border-paper-300 bg-paper-50 p-6 sm:p-8">
          <h1 className="font-serif text-[1.75rem] font-light leading-tight tracking-tight">{title}</h1>
          <p className="mt-1 text-sm text-ink-700/75">{subtitle}</p>
          <div className="mt-6">{children}</div>
        </div>
        <p className="mt-6 text-center text-sm text-ink-700">{footer}</p>
      </section>
    </div>
  )
}

export function FormError({ children }: { children: ReactNode }) {
  if (!children) return null
  return (
    <p role="alert" className="rounded-lg bg-laterite-100 px-3 py-2 text-sm text-laterite-600">
      {children}
    </p>
  )
}

export function SubmitButton({
  pending,
  disabled = false,
  inline = false,
  children,
}: {
  pending: boolean
  disabled?: boolean
  /** Content-width button for forms inside a page rather than an auth card. */
  inline?: boolean
  children: ReactNode
}) {
  return (
    <button
      type="submit"
      disabled={pending || disabled}
      className={`${
        inline
          ? 'rounded-full bg-brand-900 px-5 py-2.5 text-sm hover:bg-brand-800'
          : 'w-full rounded-full bg-pine-600 px-6 py-3 hover:bg-pine-700'
      } font-medium text-white disabled:cursor-not-allowed disabled:opacity-60`}
    >
      {pending ? 'Just a moment…' : children}
    </button>
  )
}

export function Divider({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-3 text-xs text-stone-500">
      <span className="h-px flex-1 bg-paper-300" />
      {label}
      <span className="h-px flex-1 bg-paper-300" />
    </div>
  )
}
