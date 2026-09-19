import { useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { googleSignIn, login } from '../api/auth.ts'
import { fieldErrors, messageFor } from '../auth/errorMessages.ts'
import { useAuth } from '../auth/useAuth.ts'
import { useCompleteSignIn, useRedirectTarget } from '../auth/useCompleteSignIn.ts'
import { AuthCard, FormError, SubmitButton } from '../components/auth/AuthCard.tsx'
import { GoogleSignInButton } from '../components/auth/GoogleSignInButton.tsx'
import { MethodTabs, type AuthMethodTab } from '../components/auth/MethodTabs.tsx'
import { PhoneOtpForm } from '../components/auth/PhoneOtpForm.tsx'
import { TextField } from '../components/auth/TextField.tsx'

export function LoginPage() {
  const auth = useAuth()
  const target = useRedirectTarget()
  const complete = useCompleteSignIn()
  const [tab, setTab] = useState<AuthMethodTab>('email')
  const [googleError, setGoogleError] = useState('')

  if (auth.status === 'authenticated') return <Navigate to={target} replace />

  async function onGoogle(idToken: string) {
    setGoogleError('')
    try {
      complete(await googleSignIn(idToken))
    } catch (err) {
      setGoogleError(messageFor(err))
    }
  }

  return (
    <AuthCard
      title="Welcome back"
      subtitle="Sign in to see your bookings and upcoming treks."
      footer={
        <>
          New to Sahyātri?{' '}
          <Link to="/signup" state={{ from: target }} className="font-medium text-pine-600 hover:text-pine-700">
            Create an account
          </Link>
        </>
      }
    >
      <div className="space-y-5">
        <GoogleSignInButton onCredential={(token) => void onGoogle(token)} />
        <FormError>{googleError}</FormError>
        <MethodTabs value={tab} onChange={setTab} />
        {tab === 'email' ? <EmailLoginForm /> : <PhoneOtpForm onSuccess={complete} />}
      </div>
    </AuthCard>
  )
}

function EmailLoginForm() {
  const complete = useCompleteSignIn()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const missing: Record<string, string> = {}
    if (!email.trim()) missing.email = 'Enter your email'
    if (!password) missing.password = 'Enter your password'
    setFields(missing)
    setError('')
    if (Object.keys(missing).length) return

    setPending(true)
    try {
      complete(await login({ email: email.trim(), password }))
    } catch (err) {
      setError(messageFor(err))
      setFields(fieldErrors(err))
      setPending(false)
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      <TextField
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        placeholder="you@example.com"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fields.email}
      />
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="current-password"
        placeholder="Enter your password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fields.password}
      />
      <FormError>{error}</FormError>
      <SubmitButton pending={pending}>Sign in</SubmitButton>
    </form>
  )
}
