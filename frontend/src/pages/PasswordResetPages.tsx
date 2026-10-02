import { useState, type FormEvent } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { forgotPassword, resetPassword } from '../api/auth.ts'
import { ApiError } from '../api/client.ts'
import { fieldErrors, messageFor } from '../auth/errorMessages.ts'
import { useCompleteSignIn } from '../auth/useCompleteSignIn.ts'
import { EMAIL_RULE, PASSWORD_HINT, PASSWORD_RULE } from '../auth/validation.ts'
import { AuthCard, FormError, SubmitButton } from '../components/auth/AuthCard.tsx'
import { TextField } from '../components/auth/TextField.tsx'

const backToSignIn = (
  <Link to="/login" className="font-medium text-pine-600 hover:text-pine-700">
    Back to sign in
  </Link>
)

/** /forgot-password: asks for the email and sends a reset link (docs/TRD.md §7.2). */
export function ForgotPasswordPage() {
  const prefill = (useLocation().state as { email?: string } | null)?.email ?? ''
  const [email, setEmail] = useState(prefill)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [fieldError, setFieldError] = useState('')
  const [sentTo, setSentTo] = useState('')

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const value = email.trim()
    setError('')
    setFieldError(EMAIL_RULE.test(value) ? '' : 'Enter a valid email')
    if (!EMAIL_RULE.test(value)) return

    setPending(true)
    try {
      await forgotPassword(value)
      setSentTo(value)
    } catch (err) {
      setError(messageFor(err))
      setFieldError(fieldErrors(err).email ?? '')
    } finally {
      setPending(false)
    }
  }

  if (sentTo) {
    return (
      <AuthCard title="Check your email" subtitle="The link works once and expires in 30 minutes." footer={backToSignIn}>
        <p className="text-sm text-stone-700">
          If <span className="font-medium">{sentTo}</span> has an account with us, we've sent it a link to set a new
          password. Nothing there? Check spam, or{' '}
          <button type="button" onClick={() => setSentTo('')} className="font-medium text-pine-600 hover:text-pine-700">
            try another email
          </button>
          .
        </p>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Forgot your password?"
      subtitle="Enter your account email and we'll send you a link to set a new one."
      footer={backToSignIn}
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <TextField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldError}
        />
        <FormError>{error}</FormError>
        <SubmitButton pending={pending}>Send reset link</SubmitButton>
      </form>
    </AuthCard>
  )
}

/** /reset-password?token=…: the emailed link. Setting the password also signs the user in. */
export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const complete = useCompleteSignIn()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fields, setFields] = useState<Record<string, string>>({})

  const deadLink =
    error instanceof ApiError && (error.code === 'PASSWORD_RESET_INVALID' || error.code === 'PASSWORD_RESET_EXPIRED')

  if (!token || deadLink) {
    const expired = error instanceof ApiError && error.code === 'PASSWORD_RESET_EXPIRED'
    return (
      <AuthCard
        title={expired ? 'This link has expired' : "This link doesn't work"}
        subtitle={token ? messageFor(error) : 'The link is missing its token.'}
        footer={backToSignIn}
      >
        <Link
          to="/forgot-password"
          className="block w-full rounded-full bg-pine-600 px-6 py-3 text-center font-medium text-white hover:bg-pine-700"
        >
          Send a new link
        </Link>
      </AuthCard>
    )
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const problems: Record<string, string> = {}
    if (!PASSWORD_RULE.test(password)) problems.new_password = 'Use 8–72 characters with at least one letter and one digit'
    else if (password !== confirm) problems.confirm = "Passwords don't match"
    setFields(problems)
    setError(null)
    if (Object.keys(problems).length) return

    setPending(true)
    try {
      complete(await resetPassword(token, password))
    } catch (err) {
      setError(err)
      setFields(fieldErrors(err))
      setPending(false)
    }
  }

  return (
    <AuthCard title="Set a new password" subtitle="You'll be signed in, and signed out everywhere else." footer={backToSignIn}>
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <TextField
          label="New password"
          name="new_password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
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
        <FormError>{error ? messageFor(error) : ''}</FormError>
        <SubmitButton pending={pending}>Set password and sign in</SubmitButton>
      </form>
    </AuthCard>
  )
}
