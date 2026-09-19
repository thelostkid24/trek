import type { InputHTMLAttributes, ReactNode } from 'react'

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  /** Server- or client-side message for this field. */
  error?: string
  hint?: ReactNode
  prefix?: string
  /** Unit shown after the value, e.g. "cm". */
  suffix?: string
}

export function TextField({ label, error, hint, prefix, suffix, id, className = '', ...input }: Props) {
  const fieldId = id ?? input.name
  const describedBy = error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined

  return (
    <div>
      <label htmlFor={fieldId} className="block text-sm font-medium text-stone-800">
        {label}
      </label>
      <div
        className={`mt-1.5 flex items-center rounded-(--field-radius) border bg-(--field-bg) focus-within:ring-2 focus-within:ring-(--field-ring) has-[:disabled]:bg-paper-200/60 has-[:disabled]:text-stone-500 ${
          error ? 'border-laterite-500' : 'border-(--field-border)'
        }`}
      >
        {prefix && <span className="pl-3 text-sm text-stone-500">{prefix}</span>}
        <input
          {...input}
          id={fieldId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`w-full rounded-(--field-radius) bg-transparent px-3 py-2.5 text-stone-900 outline-none placeholder:text-stone-400 disabled:text-stone-500 ${className}`}
        />
        {suffix && <span className="pr-3 text-sm text-stone-500">{suffix}</span>}
      </div>
      {error ? (
        <p id={`${fieldId}-error`} className="mt-1 text-sm text-laterite-600">
          {error}
        </p>
      ) : hint ? (
        <p id={`${fieldId}-hint`} className="mt-1 text-xs text-stone-500">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
