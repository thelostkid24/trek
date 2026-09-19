import { useEffect, useRef } from 'react'
import { Divider } from './AuthCard.tsx'

const CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined
const SCRIPT_SRC = 'https://accounts.google.com/gsi/client'

type CredentialResponse = { credential?: string }
type GoogleIdentityServices = {
  accounts: {
    id: {
      initialize: (config: { client_id: string; callback: (response: CredentialResponse) => void }) => void
      renderButton: (parent: HTMLElement, options: Record<string, string | number>) => void
    }
  }
}

declare global {
  interface Window {
    google?: GoogleIdentityServices
  }
}

function loadScript(): Promise<GoogleIdentityServices> {
  return new Promise((resolve, reject) => {
    if (window.google) return resolve(window.google)
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_SRC}"]`)
    const script = existing ?? Object.assign(document.createElement('script'), { src: SCRIPT_SRC, async: true })
    script.addEventListener('load', () => (window.google ? resolve(window.google) : reject(new Error('no google'))))
    script.addEventListener('error', () => reject(new Error('Google script failed to load')))
    if (!existing) document.head.appendChild(script)
  })
}

/**
 * Renders Google's own button. It returns an ID token, which we hand to POST /api/auth/google.
 * Nothing renders until VITE_GOOGLE_CLIENT_ID is set, since the backend can't verify tokens without it.
 */
export function GoogleSignInButton({ onCredential }: { onCredential: (idToken: string) => void }) {
  const container = useRef<HTMLDivElement>(null)
  const callback = useRef(onCredential)

  useEffect(() => {
    callback.current = onCredential
  }, [onCredential])

  useEffect(() => {
    if (!CLIENT_ID) return
    let cancelled = false
    loadScript()
      .then((google) => {
        if (cancelled || !container.current) return
        google.accounts.id.initialize({
          client_id: CLIENT_ID,
          callback: (response) => {
            if (response.credential) callback.current(response.credential)
          },
        })
        google.accounts.id.renderButton(container.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          width: 320,
          text: 'continue_with',
        })
      })
      .catch(() => {
        // Leave the slot empty — email and phone sign-in still work.
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (!CLIENT_ID) return null
  return (
    <div className="space-y-5">
      <div ref={container} className="flex min-h-[44px] justify-center" />
      <Divider label="or" />
    </div>
  )
}
