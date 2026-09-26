import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent, type ReactNode } from 'react'
import { changePassword, requestEmailChange, requestPhoneCode, verifyPhone } from '../../api/account.ts'
import type { AuthMethod, User } from '../../api/auth.ts'
import { ApiError } from '../../api/client.ts'
import { fieldErrors, messageFor, retryAfter } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { useCountdown } from '../../auth/useCountdown.ts'
import { formatPhone } from '../../lib/format.ts'
import { EMAIL_RULE, INDIAN_MOBILE, PASSWORD_HINT, PASSWORD_RULE } from '../../auth/validation.ts'
import { FormError } from '../auth/AuthCard.tsx'
import { TextField } from '../auth/TextField.tsx'
import { Badge, PrimaryButton, ProfileSection, SecondaryButton } from './ProfileSection.tsx'

type Panel = 'email' | 'phone' | 'password' | null

const METHOD_LABELS: Record<AuthMethod, string> = {
  PASSWORD: 'Email & password',
  PHONE_OTP: 'Mobile OTP',
  GOOGLE: 'Google',
}

/** Email, phone and password. Each row opens its own small form; only one is open at a time. */
export function SecuritySection({ user }: { user: User }) {
  const [open, setOpen] = useState<Panel>(null)
  const toggle = (panel: Exclude<Panel, null>) => setOpen((current) => (current === panel ? null : panel))
  const hasPassword = user.auth_methods.includes('PASSWORD')

  return (
    <ProfileSection id="security" title="Sign-in & security" description="How you sign in, and where we reach you about bookings.">
      <div className="divide-y divide-paper-300">
        <Row
          label="Email"
          value={user.email ?? 'No email yet'}
          muted={!user.email}
          badge={user.email && <VerifiedBadge verified={user.email_verified} />}
          action={user.email ? 'Change' : 'Add'}
          expanded={open === 'email'}
          onAction={() => toggle('email')}
          note={user.email && !user.email_verified && open !== 'email' && <VerifyEmailNudge email={user.email} />}
        >
          <EmailPanel user={user} onDone={() => setOpen(null)} />
        </Row>

        <Row
          label="Mobile"
          value={user.phone ? formatPhone(user.phone) : 'No mobile number yet'}
          muted={!user.phone}
          badge={user.phone && <VerifiedBadge verified={user.phone_verified} />}
          action={user.phone ? 'Change' : 'Add'}
          expanded={open === 'phone'}
          onAction={() => toggle('phone')}
        >
          <PhonePanel user={user} onDone={() => setOpen(null)} />
        </Row>

        <Row
          label="Password"
          value={hasPassword ? '••••••••' : 'Not set'}
          muted={!hasPassword}
          action={hasPassword ? 'Change' : 'Set password'}
          expanded={open === 'password'}
          onAction={() => toggle('password')}
        >
          <PasswordPanel user={user} hasPassword={hasPassword} onDone={() => setOpen(null)} />
        </Row>
      </div>

      <div className="mt-6 border-t border-paper-300 pt-5">
        <p className="text-sm font-medium text-stone-800">You can sign in with</p>
        <ul className="mt-2 flex flex-wrap gap-2">
          {user.auth_methods.map((method) => (
            <li key={method} className="rounded-full bg-pine-600/10 px-3 py-1 text-xs font-medium text-pine-700">
              {METHOD_LABELS[method]}
            </li>
          ))}
        </ul>
      </div>
    </ProfileSection>
  )
}

function Row({
  label,
  value,
  muted,
  badge,
  action,
  expanded,
  onAction,
  note,
  children,
}: {
  label: string
  value: string
  muted: boolean
  badge?: ReactNode
  action: string
  expanded: boolean
  onAction: () => void
  /** Shown under the value while the row is closed, e.g. the verify-email prompt. */
  note?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="py-5 first:pt-0 last:pb-0">
      <div className="flex items-center justify-between gap-4 sm:grid sm:grid-cols-[7.5rem_minmax(0,1fr)_auto]">
        <p className="hidden text-xs font-medium tracking-wide text-stone-500 uppercase sm:block">{label}</p>
        <div className="min-w-0">
          <p className="text-xs font-medium tracking-wide text-stone-500 uppercase sm:hidden">{label}</p>
          <div className="mt-1 flex flex-wrap items-center gap-x-2.5 gap-y-1.5 sm:mt-0">
            <span className={`truncate ${muted ? 'text-stone-400' : 'text-stone-900'}`}>{value}</span>
            {badge}
          </div>
        </div>
        <SecondaryButton onClick={onAction} aria-expanded={expanded} className="min-w-[5.5rem] shrink-0">
          {expanded ? 'Cancel' : action}
        </SecondaryButton>
      </div>
      {note && <div className="mt-3 sm:ml-[8.5rem]">{note}</div>}
      {expanded && (
        <div className="mt-5 rounded-xl border border-paper-300 bg-paper-100/70 p-5 sm:ml-[8.5rem] sm:p-6">
          <div className="max-w-md">{children}</div>
        </div>
      )}
    </div>
  )
}

function VerifiedBadge({ verified }: { verified: boolean }) {
  return verified ? <Badge tone="good">Verified</Badge> : <Badge tone="warn">Not verified</Badge>
}

/** Shared by "verify my current email" and "switch to a new email" — the server tells them apart. */
function useSendEmailLink() {
  const { withAuth } = useAuth()
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const mutation = useMutation({
    mutationFn: (email: string) => withAuth((token) => requestEmailChange(token, email)),
    onMutate: () => {
      setError('')
      setFields({})
    },
    onSuccess: (_, email) => setSentTo(email),
    onError: (err) => {
      const wait = retryAfter(err)
      setError(wait ? `${messageFor(err)} (about ${Math.ceil(wait / 60)} min)` : messageFor(err))
      setFields(fieldErrors(err))
    },
  })
  return { send: mutation.mutate, pending: mutation.isPending, sentTo, error, fields, setFields }
}

function LinkSent({ email }: { email: string }) {
  return (
    <p className="text-sm text-stone-700" role="status">
      We've sent a link to <span className="font-medium text-stone-900">{email}</span>. Open it within 24 hours to
      finish. Nothing changes until you do.
    </p>
  )
}

function VerifyEmailNudge({ email }: { email: string }) {
  const { send, pending, sentTo, error } = useSendEmailLink()
  return (
    <div>
      {sentTo ? (
        <LinkSent email={sentTo} />
      ) : (
        <p className="text-sm text-stone-600">
          Verify this address so booking confirmations reach you.{' '}
          <button
            type="button"
            disabled={pending}
            onClick={() => send(email)}
            className="font-medium text-pine-700 underline hover:text-pine-600 disabled:text-stone-400"
          >
            {pending ? 'Sending…' : 'Send verification link'}
          </button>
        </p>
      )}
      {error && (
        <div className="mt-2">
          <FormError>{error}</FormError>
        </div>
      )}
    </div>
  )
}

function EmailPanel({ user, onDone }: { user: User; onDone: () => void }) {
  const [email, setEmail] = useState('')
  const { send, pending, sentTo, error, fields, setFields } = useSendEmailLink()

  if (sentTo) {
    return (
      <div className="space-y-5">
        <LinkSent email={sentTo} />
        <button type="button" onClick={onDone} className="text-sm font-medium text-pine-700 hover:text-pine-600">
          Done
        </button>
      </div>
    )
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    const value = email.trim()
    if (!EMAIL_RULE.test(value)) {
      setFields({ email: 'Enter a valid email address' })
      return
    }
    send(value)
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      <TextField
        label={user.email ? 'New email' : 'Email'}
        name="new_email"
        type="email"
        autoComplete="email"
        autoFocus
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fields.email}
        hint="We'll email a link to confirm it's yours."
      />
      <FormError>{error}</FormError>
      <PrimaryButton pending={pending}>
        Send link
      </PrimaryButton>
    </form>
  )
}

function PhonePanel({ user, onDone }: { user: User; onDone: () => void }) {
  const { withAuth, updateUser } = useAuth()
  const queryClient = useQueryClient()
  const [digits, setDigits] = useState('')
  const [code, setCode] = useState('')
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [cooldown, setCooldown] = useCountdown()
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})

  const fail = (err: unknown) => {
    setError(messageFor(err))
    setFields(fieldErrors(err))
  }
  const reset = () => {
    setError('')
    setFields({})
  }

  const sendCode = useMutation({
    mutationFn: (phone: string) => withAuth((token) => requestPhoneCode(token, phone)),
    onMutate: reset,
    onSuccess: (res, phone) => {
      setSentTo(phone)
      setCode('')
      setCooldown(res.resend_after)
    },
    onError: (err) => {
      fail(err)
      const wait = retryAfter(err)
      if (wait) setCooldown(wait)
    },
  })
  const verify = useMutation({
    mutationFn: ({ phone, otp }: { phone: string; otp: string }) => withAuth((token) => verifyPhone(token, phone, otp)),
    onMutate: reset,
    onSuccess: (updated) => {
      updateUser(updated)
      void queryClient.invalidateQueries({ queryKey: ['trekker-profile', updated.id] })
      onDone()
    },
    onError: fail,
  })

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!sentTo) {
      if (!INDIAN_MOBILE.test(digits)) {
        setFields({ phone: 'Enter a 10-digit Indian mobile number' })
        return
      }
      sendCode.mutate(`+91${digits}`)
    } else {
      if (!/^\d{6}$/.test(code)) {
        setFields({ code: 'Enter the 6-digit code' })
        return
      }
      verify.mutate({ phone: sentTo, otp: code })
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {!sentTo ? (
        <>
          <TextField
            label={user.phone ? 'New mobile number' : 'Mobile number'}
            name="new_phone"
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            autoFocus
            prefix="+91"
            placeholder="98765 43210"
            value={digits}
            onChange={(e) => setDigits(e.target.value.replace(/\D/g, '').slice(0, 10))}
            error={fields.phone}
            hint="We'll text you a 6-digit code."
          />
          <FormError>{error}</FormError>
          <PrimaryButton pending={sendCode.isPending} disabled={cooldown > 0}>
            {cooldown > 0 ? `Send code (${cooldown}s)` : 'Send code'}
          </PrimaryButton>
        </>
      ) : (
        <>
          <p className="text-sm text-stone-600">
            Code sent to <span className="font-medium text-stone-900">{sentTo}</span>.{' '}
            <button
              type="button"
              onClick={() => {
                setSentTo(null)
                reset()
              }}
              className="text-pine-700 underline hover:text-pine-600"
            >
              Use a different number
            </button>
          </p>
          <TextField
            label="6-digit code"
            name="phone_code"
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            error={fields.code}
            className="tracking-[0.4em]"
          />
          <FormError>{error}</FormError>
          <div className="flex flex-wrap items-center gap-3">
            <PrimaryButton pending={verify.isPending}>
              Verify number
            </PrimaryButton>
            <button
              type="button"
              onClick={() => sendCode.mutate(sentTo)}
              disabled={sendCode.isPending || cooldown > 0}
              className="text-sm text-pine-700 hover:text-pine-600 disabled:text-stone-400"
            >
              {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
            </button>
          </div>
        </>
      )}
    </form>
  )
}

function PasswordPanel({ user, hasPassword, onDone }: { user: User; hasPassword: boolean; onDone: () => void }) {
  const { withAuth, setSession } = useAuth()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const [done, setDone] = useState(false)

  const change = useMutation({
    mutationFn: () =>
      withAuth((token) =>
        changePassword(token, { ...(hasPassword ? { current_password: current } : {}), new_password: next }),
      ),
    onSuccess: (session) => {
      setSession(session)
      setDone(true)
    },
    onError: (err) => {
      setError(messageFor(err))
      const byField = fieldErrors(err)
      // Surface the "wrong current password" error on its field too.
      if (err instanceof ApiError && err.code === 'CURRENT_PASSWORD_INCORRECT') {
        byField.current_password = 'Incorrect password'
      }
      setFields(byField)
    },
  })

  if (!user.email) {
    return (
      <p className="text-sm text-stone-700">
        Passwords are used with your email to sign in. Add an email above first, then come back to set one.
      </p>
    )
  }

  if (done) {
    return (
      <div className="space-y-4" role="status">
        <p className="text-sm text-stone-700">
          Password {hasPassword ? 'updated' : 'set'}. You're still signed in here; other devices were signed out.
        </p>
        <button type="button" onClick={onDone} className="text-sm font-medium text-pine-700 hover:text-pine-600">
          Done
        </button>
      </div>
    )
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    const problems: Record<string, string> = {}
    if (hasPassword && !current) problems.current_password = 'Enter your current password'
    if (!PASSWORD_RULE.test(next)) problems.new_password = PASSWORD_HINT
    else if (next !== confirm) problems.confirm = "Passwords don't match"
    setFields(problems)
    if (Object.keys(problems).length === 0) change.mutate()
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-5">
      {/* Lets password managers attach the new password to the right account. */}
      <input type="text" name="username" autoComplete="username" value={user.email} readOnly hidden />
      {hasPassword && (
        <TextField
          label="Current password"
          name="current_password"
          type="password"
          autoComplete="current-password"
          autoFocus
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          error={fields.current_password}
        />
      )}
      <TextField
        label="New password"
        name="new_password"
        type="password"
        autoComplete="new-password"
        autoFocus={!hasPassword}
        value={next}
        onChange={(e) => setNext(e.target.value)}
        error={fields.new_password}
        hint={PASSWORD_HINT}
      />
      <TextField
        label="Confirm new password"
        name="confirm"
        type="password"
        autoComplete="new-password"
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
        error={fields.confirm}
      />
      <p className="text-xs text-stone-500">Saving signs you out on your other devices.</p>
      <FormError>{error}</FormError>
      <PrimaryButton pending={change.isPending}>
        {hasPassword ? 'Change password' : 'Set password'}
      </PrimaryButton>
    </form>
  )
}
