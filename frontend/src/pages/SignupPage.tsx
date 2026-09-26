import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate } from 'react-router-dom'
import type { SignupChoices } from '../analytics/attribution.ts'
import { googleSignIn, signup } from '../api/auth.ts'
import { fieldErrors, messageFor } from '../auth/errorMessages.ts'
import { useAuth } from '../auth/useAuth.ts'
import { useCompleteSignIn, useRedirectTarget } from '../auth/useCompleteSignIn.ts'
import { EMAIL_RULE, PASSWORD_RULE } from '../auth/validation.ts'
import { AuthCard, FormError, SubmitButton } from '../components/auth/AuthCard.tsx'
import { GoogleSignInButton } from '../components/auth/GoogleSignInButton.tsx'
import { MethodTabs, type AuthMethodTab } from '../components/auth/MethodTabs.tsx'
import { PhoneOtpForm } from '../components/auth/PhoneOtpForm.tsx'
import { SignupChoicesFields } from '../components/auth/SignupChoicesFields.tsx'
import { TextField } from '../components/auth/TextField.tsx'

export function SignupPage() {
  const auth = useAuth()
  const target = useRedirectTarget()
  const complete = useCompleteSignIn()
  const [tab, setTab] = useState<AuthMethodTab>('email')
  const [googleError, setGoogleError] = useState('')
  // Shared by all three methods, so switching tabs keeps the answers.
  const [choices, setChoices] = useState<SignupChoices>({})

  if (auth.status === 'authenticated') return <Navigate to={target} replace />

  async function onGoogle(idToken: string) {
    setGoogleError('')
    try {
      complete(await googleSignIn(idToken, choices))
    } catch (err) {
      setGoogleError(messageFor(err))
    }
  }

  return (
    <AuthCard
      title="Create your account"
      subtitle="Book a seat on a small-batch trek with a local guide."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" state={{ from: target }} className="font-medium text-pine-600 hover:text-pine-700">
            Sign in
          </Link>
        </>
      }
    >
      <div className="space-y-5">
        <GoogleSignInButton onCredential={(token) => void onGoogle(token)} />
        <FormError>{googleError}</FormError>
        <MethodTabs value={tab} onChange={setTab} />
        {tab === 'email' ? (
          <EmailSignupForm choices={choices} extraFields={<SignupChoicesFields value={choices} onChange={setChoices} />} />
        ) : (
          <PhoneOtpForm
            onSuccess={complete}
            askName
            choices={choices}
            extraFields={<SignupChoicesFields value={choices} onChange={setChoices} />}
          />
        )}
      </div>
    </AuthCard>
  )
}

function EmailSignupForm({ choices, extraFields }: { choices: SignupChoices; extraFields: ReactNode }) {
  const complete = useCompleteSignIn()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})

  function validate(): Record<string, string> {
    const problems: Record<string, string> = {}
    if (!fullName.trim()) problems.full_name = 'Enter your name'
    if (!EMAIL_RULE.test(email.trim())) problems.email = 'Enter a valid email'
    if (!PASSWORD_RULE.test(password)) problems.password = 'Use 8–72 characters with at least one letter and one digit'
    return problems
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    const problems = validate()
    setFields(problems)
    setError('')
    if (Object.keys(problems).length) return

    setPending(true)
    try {
      complete(await signup({ full_name: fullName.trim(), email: email.trim(), password }, choices))
    } catch (err) {
      setError(messageFor(err))
      setFields(fieldErrors(err))
      setPending(false)
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4">
      <TextField
        label="Full name"
        name="full_name"
        autoComplete="name"
        placeholder="Your full name"
        maxLength={100}
        value={fullName}
        onChange={(e) => setFullName(e.target.value)}
        error={fields.full_name}
      />
      <TextField
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        placeholder="you@example.com"
        maxLength={254}
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={fields.email}
      />
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="new-password"
        placeholder="Create a password"
        maxLength={72}
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={fields.password}
        hint="At least 8 characters, with a letter and a digit."
      />
      {extraFields}
      <FormError>{error}</FormError>
      <SubmitButton pending={pending}>Create account</SubmitButton>
    </form>
  )
}
