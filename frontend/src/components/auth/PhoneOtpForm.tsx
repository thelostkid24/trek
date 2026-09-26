import { useState, type FormEvent, type ReactNode } from 'react'
import type { SignupChoices } from '../../analytics/attribution.ts'
import { requestOtp, verifyOtp, type AuthResponse } from '../../api/auth.ts'
import { fieldErrors, messageFor, retryAfter } from '../../auth/errorMessages.ts'
import { useCountdown } from '../../auth/useCountdown.ts'
import { INDIAN_MOBILE } from '../../auth/validation.ts'
import { FormError, SubmitButton } from './AuthCard.tsx'
import { TextField } from './TextField.tsx'

/**
 * Two steps: send a code to +91 number, then verify it. Unknown numbers get an account on verify,
 * so the same form serves sign-in and sign-up; `askName` adds the optional name field for sign-up, and
 * `extraFields` (shown with the number) collect the `choices` sent with the code.
 */
export function PhoneOtpForm({
  onSuccess,
  askName = false,
  choices,
  extraFields,
}: {
  onSuccess: (r: AuthResponse) => void
  askName?: boolean
  choices?: SignupChoices
  extraFields?: ReactNode
}) {
  const [digits, setDigits] = useState('')
  const [fullName, setFullName] = useState('')
  const [code, setCode] = useState('')
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [cooldown, setCooldown] = useCountdown()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})

  const phone = `+91${digits}`

  async function sendCode() {
    if (!INDIAN_MOBILE.test(digits)) {
      setFields({ phone: 'Enter a 10-digit Indian mobile number' })
      return
    }
    setPending(true)
    setError('')
    setFields({})
    try {
      const res = await requestOtp(phone)
      setSentTo(phone)
      setCode('')
      setCooldown(res.resend_after)
    } catch (err) {
      setError(messageFor(err))
      setFields(fieldErrors(err))
      const wait = retryAfter(err)
      if (wait) setCooldown(wait)
    } finally {
      setPending(false)
    }
  }

  async function verify() {
    if (!sentTo) return
    if (!/^\d{6}$/.test(code)) {
      setFields({ code: 'Enter the 6-digit code' })
      return
    }
    setPending(true)
    setError('')
    setFields({})
    try {
      const name = fullName.trim()
      onSuccess(await verifyOtp({ phone: sentTo, code, ...(name ? { full_name: name } : {}) }, choices))
    } catch (err) {
      setError(messageFor(err))
      setFields(fieldErrors(err))
      setPending(false)
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    void (sentTo ? verify() : sendCode())
  }

  function changeNumber() {
    setSentTo(null)
    setCode('')
    setError('')
    setFields({})
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      {!sentTo ? (
        <>
          {askName && (
            <TextField
              label="Full name"
              name="full_name"
              autoComplete="name"
              placeholder="Your full name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              error={fields.full_name}
              maxLength={100}
            />
          )}
          <TextField
            label="Mobile number"
            name="phone"
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            prefix="+91"
            placeholder="98765 43210"
            value={digits}
            onChange={(e) => setDigits(e.target.value.replace(/\D/g, '').slice(0, 10))}
            error={fields.phone}
            hint="We'll text you a 6-digit code."
          />
          {extraFields}
          <FormError>{error}</FormError>
          <SubmitButton pending={pending} disabled={cooldown > 0}>{cooldown > 0 ? `Send code (${cooldown}s)` : 'Send code'}</SubmitButton>
        </>
      ) : (
        <>
          <p className="text-sm text-stone-600">
            Code sent to <span className="font-medium text-stone-900">{sentTo}</span>.{' '}
            <button type="button" onClick={changeNumber} className="text-pine-600 underline hover:text-pine-700">
              Change
            </button>
          </p>
          <TextField
            label="6-digit code"
            name="code"
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
            placeholder="••••••"
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            error={fields.code}
            className="tracking-[0.4em]"
          />
          <FormError>{error}</FormError>
          <SubmitButton pending={pending}>Verify and continue</SubmitButton>
          <button
            type="button"
            onClick={() => void sendCode()}
            disabled={pending || cooldown > 0}
            className="w-full text-center text-sm text-pine-600 hover:text-pine-700 disabled:text-stone-400"
          >
            {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
          </button>
        </>
      )}
    </form>
  )
}
