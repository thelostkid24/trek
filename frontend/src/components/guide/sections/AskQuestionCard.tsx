import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { askAsTrekker, askAsVisitor, MAX_MESSAGE, requestQuestionEmailCode } from '../../../api/questions.ts'
import { messageFor } from '../../../auth/errorMessages.ts'
import { useAuth } from '../../../auth/useAuth.ts'
import { EMAIL_RULE } from '../../../auth/validation.ts'
import { TextField } from '../../auth/TextField.tsx'
import type { GuideSectionProps } from './types.ts'

const FIELD =
  'mt-1.5 w-full rounded-(--field-radius) border border-(--field-border) bg-(--field-bg) px-3 py-2.5 text-stone-900 outline-none placeholder:text-stone-500 focus:ring-2 focus:ring-(--field-ring)'
const OUTLINE =
  'rounded-full border border-ink-900 px-5 py-2.5 text-sm font-medium text-ink-900 transition hover:bg-ink-900 hover:text-white disabled:opacity-50'

/**
 * "Ask <first name> a question" (§7.21.6). A signed-in trekker sends at once; anyone else gives a name and email and
 * confirms the email with a code first. Contact details never reach the guide: they reply on the site. Hidden for the
 * guide themself.
 */
export function AskQuestionCard({ guide, owner }: GuideSectionProps) {
  const auth = useAuth()
  const [body, setBody] = useState('')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [codeSentTo, setCodeSentTo] = useState<string | null>(null)
  const [done, setDone] = useState<{ accountThread: string | null } | null>(null)
  const [touched, setTouched] = useState(false)

  const trekker = auth.status === 'authenticated' && auth.user.role === 'TREKKER'
  const first = (guide.full_name ?? 'the guide').split(' ')[0]
  const key = guide.slug ?? guide.id
  const question = body.trim()

  const askSignedIn = useMutation({
    mutationFn: () => auth.withAuth((t) => askAsTrekker(t, key, question)),
    onSuccess: (thread) => setDone({ accountThread: thread.id }),
  })
  const sendCode = useMutation({
    mutationFn: (to: string) => requestQuestionEmailCode(to),
    onSuccess: (_, to) => {
      setCodeSentTo(to)
      setCode('')
    },
  })
  const askVisitor = useMutation({
    mutationFn: () =>
      askAsVisitor(key, { name: name.trim(), email: codeSentTo ?? '', email_code: code, body: question }),
    onSuccess: () => setDone({ accountThread: null }),
  })

  if (owner) return null

  const reset = () => {
    setDone(null)
    setBody('')
    setCodeSentTo(null)
    setCode('')
    askSignedIn.reset()
    askVisitor.reset()
  }

  const emailOk = EMAIL_RULE.test(email.trim())
  const visitorReady = question !== '' && name.trim() !== '' && emailOk
  const error = askSignedIn.error ?? sendCode.error ?? askVisitor.error

  const submit = () => {
    setTouched(true)
    if (trekker) {
      if (question) askSignedIn.mutate()
    } else if (codeSentTo) {
      if (code.length === 6) askVisitor.mutate()
    } else if (visitorReady) {
      sendCode.mutate(email.trim().toLowerCase())
    }
  }

  return (
    <section aria-labelledby="ask-question" className="rounded-(--card-radius) border border-paper-300 bg-white p-5 sm:p-6">
      <h2 id="ask-question" className="font-display text-xl text-stone-900">
        Ask {first} a question
      </h2>
      {done ? (
        <div className="mt-3 space-y-2 text-sm text-stone-700" role="status">
          <p>
            Sent. {first} gets it now and replies here on Empty Valley; we'll email you when they do.
          </p>
          {done.accountThread ? (
            <Link to={`/account/questions/${done.accountThread}`} className="font-medium text-brand-800 underline">
              See it in your account
            </Link>
          ) : (
            <p>Check your inbox for a link to the conversation.</p>
          )}
          <button type="button" onClick={reset} className="block font-medium text-brand-800 underline">
            Ask something else
          </button>
        </div>
      ) : (
        <form
          className="mt-4 space-y-4"
          noValidate
          onSubmit={(e) => {
            e.preventDefault()
            submit()
          }}
        >
          <div>
            <label htmlFor="question-body" className="text-[0.65rem] font-medium tracking-[0.12em] text-stone-500 uppercase">
              Your question
            </label>
            <textarea
              id="question-body"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              maxLength={MAX_MESSAGE}
              rows={4}
              disabled={codeSentTo !== null}
              placeholder="Is Kedarkantha in December OK for a first trek?"
              className={FIELD}
            />
            {touched && !question && <p className="mt-1 text-sm text-laterite-600">Write your question first.</p>}
          </div>

          {!trekker && codeSentTo === null && (
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField label="Your name" name="question-name" autoComplete="name" maxLength={100} value={name}
                onChange={(e) => setName(e.target.value)}
                error={touched && !name.trim() ? 'Tell us your name.' : undefined} />
              <TextField label="Your email" name="question-email" type="email" autoComplete="email" maxLength={254}
                value={email} onChange={(e) => setEmail(e.target.value)}
                error={touched && !emailOk ? 'Enter a valid email.' : undefined}
                hint="We send their reply here. They never see it." />
            </div>
          )}

          {codeSentTo !== null && (
            <div className="rounded-xl bg-paper-100 p-4">
              <p className="text-sm text-stone-700">
                We emailed a 6-digit code to <span className="font-medium break-all text-stone-900">{codeSentTo}</span>.
                Enter it to send your question.
              </p>
              <div className="mt-3">
                <TextField label="Code" name="question-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
                  placeholder="123456" value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} />
              </div>
              <p className="mt-2 text-xs text-stone-600">
                Nothing arrived? Check spam, or{' '}
                <button type="button" disabled={sendCode.isPending} onClick={() => sendCode.mutate(codeSentTo)}
                  className="font-medium text-brand-800 underline disabled:opacity-50">
                  send a new code
                </button>
                .{' '}
                <button type="button" onClick={() => setCodeSentTo(null)} className="font-medium text-brand-800 underline">
                  Change details
                </button>
              </p>
            </div>
          )}

          {error && (
            <p role="alert" className="text-sm text-laterite-600">
              {messageFor(error)}
            </p>
          )}

          <div className="flex flex-wrap items-center gap-3">
            <button type="submit" className={OUTLINE}
              disabled={askSignedIn.isPending || sendCode.isPending || askVisitor.isPending || (codeSentTo !== null && code.length !== 6)}>
              {askSignedIn.isPending || askVisitor.isPending
                ? 'Sending…'
                : codeSentTo !== null
                  ? 'Confirm and send'
                  : 'Send'}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
