import type { ButtonHTMLAttributes, ReactNode } from 'react'

/** A titled block on the profile page, divided from the one above by a hairline. */
export function ProfileSection({
  id,
  title,
  description,
  badge,
  children,
}: {
  id?: string
  title: string
  description?: ReactNode
  badge?: ReactNode
  children: ReactNode
}) {
  return (
    <section id={id} className="scroll-mt-20 border-t border-paper-300 pt-8">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="font-display text-xl font-medium text-stone-900">{title}</h2>
        {badge}
      </div>
      {description && <p className="mt-1 text-sm text-stone-600">{description}</p>}
      <div className="mt-5">{children}</div>
    </section>
  )
}

export function Badge({ tone, children }: { tone: 'good' | 'warn' | 'muted'; children: ReactNode }) {
  const tones = {
    good: 'bg-pine-600/10 text-pine-700',
    warn: 'bg-laterite-100 text-laterite-600',
    muted: 'bg-paper-200 text-stone-600',
  }
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${tones[tone]}`}>
      {children}
    </span>
  )
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { pending?: boolean }

/** Solid pine button used across the account area. Defaults to type="submit". */
export function PrimaryButton({ pending = false, disabled, type = 'submit', className = '', children, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      type={type}
      disabled={pending || disabled}
      className={`rounded-(--field-radius) bg-pine-600 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-pine-700 disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
    >
      {pending ? 'Just a moment…' : children}
    </button>
  )
}

/** Outlined companion to PrimaryButton. Defaults to type="button". */
export function SecondaryButton({ type = 'button', className = '', children, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      type={type}
      className={`rounded-(--field-radius) border border-paper-300 bg-paper-50 px-4 py-2 text-sm font-medium text-stone-800 transition hover:border-pine-600 hover:text-pine-700 disabled:opacity-60 ${className}`}
    >
      {children}
    </button>
  )
}
