import type { ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'

// Same frame as components/auth/TextField.tsx, for the other input types the profile needs.

const frame = (error?: string) =>
  `mt-1.5 w-full rounded-(--field-radius) border bg-(--field-bg) px-3 py-2.5 text-stone-900 outline-none focus:ring-2 focus:ring-(--field-ring) ${
    error ? 'border-laterite-500' : 'border-(--field-border)'
  }`

function Message({ id, error, hint }: { id: string; error?: string; hint?: ReactNode }) {
  if (error) {
    return (
      <p id={`${id}-error`} className="mt-1 text-sm text-laterite-600">
        {error}
      </p>
    )
  }
  if (hint) {
    return (
      <p id={`${id}-hint`} className="mt-1 text-xs text-stone-500">
        {hint}
      </p>
    )
  }
  return null
}

const describedBy = (id: string, error?: string, hint?: ReactNode) =>
  error ? `${id}-error` : hint ? `${id}-hint` : undefined

type SelectProps = Omit<SelectHTMLAttributes<HTMLSelectElement>, 'children'> & {
  label: string
  name: string
  options: { value: string; label: string }[]
  /** Label of the empty option that maps to null. */
  placeholder?: string
  error?: string
  hint?: ReactNode
}

export function SelectField({ label, name, options, placeholder = 'Select…', error, hint, ...select }: SelectProps) {
  return (
    <div>
      <label htmlFor={name} className="block text-sm font-medium text-stone-800">
        {label}
      </label>
      <select
        {...select}
        id={name}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(name, error, hint)}
        className={frame(error)}
      >
        <option value="">{placeholder}</option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      <Message id={name} error={error} hint={hint} />
    </div>
  )
}

type TextAreaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  label: string
  name: string
  maxLength: number
  value: string
  error?: string
  hint?: ReactNode
}

export function TextAreaField({ label, name, maxLength, value, error, hint, ...textarea }: TextAreaProps) {
  return (
    <div>
      <div className="flex items-baseline justify-between">
        <label htmlFor={name} className="block text-sm font-medium text-stone-800">
          {label}
        </label>
        <span className={`text-xs ${value.length > maxLength * 0.9 ? 'text-laterite-600' : 'text-stone-400'}`}>
          {value.length}/{maxLength}
        </span>
      </div>
      <textarea
        {...textarea}
        id={name}
        name={name}
        value={value}
        maxLength={maxLength}
        rows={textarea.rows ?? 3}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(name, error, hint)}
        className={`${frame(error)} resize-y`}
      />
      <Message id={name} error={error} hint={hint} />
    </div>
  )
}
