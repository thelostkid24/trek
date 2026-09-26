import type { ButtonHTMLAttributes, ReactNode } from 'react'

/**
 * A titled card on the profile page; the title row is ruled off from the fields.
 * While a field inside has focus, a pine bar marks the card as the one being edited.
 */
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
    <section
      id={id}
      className="profile-card group/section relative scroll-mt-24 rounded-(--card-radius) border border-paper-300 bg-paper-50 transition-[border-color,box-shadow] duration-300 focus-within:border-pine-600/30 focus-within:shadow-[0_10px_30px_-18px_rgb(23_28_35/0.3)]"
    >
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-4 -left-px w-0.5 origin-top scale-y-0 rounded-full bg-pine-600 transition-transform duration-300 ease-out group-focus-within/section:scale-y-100"
      />
      <div className="border-b border-paper-300 px-5 py-4 sm:px-7">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="font-display text-xl font-medium text-stone-900">{title}</h2>
          {badge}
        </div>
        {description && <p className="mt-1 text-sm text-stone-600">{description}</p>}
      </div>
      <div className="px-5 py-6 sm:px-7">{children}</div>
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
      className={`rounded-full bg-pine-600 px-5 py-2.5 text-sm font-medium text-white transition hover:bg-pine-700 disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
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
      className={`rounded-full border border-paper-300 bg-paper-50 px-4 py-2 text-sm font-medium text-stone-800 transition hover:border-pine-600 hover:text-pine-700 disabled:opacity-60 ${className}`}
    >
      {children}
    </button>
  )
}
