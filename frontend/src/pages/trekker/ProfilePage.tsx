import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import type { User } from '../../api/auth.ts'
import { getProfile } from '../../api/profile.ts'
import { messageFor } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { GuestNotice } from '../../components/booking/GuestNotice.tsx'
import { CommunicationSection } from '../../components/profile/CommunicationSection.tsx'
import { ProfileForm } from '../../components/profile/ProfileForm.tsx'
import { ProfileHeader } from '../../components/profile/ProfileHeader.tsx'
import { PrimaryButton } from '../../components/profile/ProfileSection.tsx'
import { SecuritySection } from '../../components/profile/SecuritySection.tsx'

/** /account/profile — rendered inside <RequireAuth role="TREKKER">. */
export function ProfilePage() {
  const auth = useAuth()
  const user = auth.status === 'authenticated' ? auth.user : null
  if (!user) return null
  return <Profile user={user} />
}

function Profile({ user }: { user: User }) {
  const { withAuth } = useAuth()
  const location = useLocation()
  const welcome = (location.state as { welcome?: boolean } | null)?.welcome === true
  const profile = useQuery({
    queryKey: ['trekker-profile', user.id],
    queryFn: () => withAuth(getProfile),
  })

  return (
    <div className="px-5 py-8 sm:px-10 sm:py-10 lg:grid lg:grid-cols-[minmax(0,48rem)_11rem] lg:gap-12 xl:gap-16">
      <div className="min-w-0 space-y-6">
        <header>
          <h1 className="text-2xl font-light tracking-[-0.02em] text-ink-900">My profile</h1>
          <p className="mt-1 text-sm text-ink-700/70">Used for your bookings and for the guide to reach you on the trail.</p>
        </header>

        {welcome && !user.full_name && (
          <div className="rounded-(--card-radius) border border-laterite-400/40 bg-laterite-100 px-5 py-4 text-sm text-laterite-600" role="status">
            <p className="font-semibold">Welcome to The Empty Valley!</p>
            <p className="mt-0.5">Add your name below so your guide knows who's coming.</p>
          </div>
        )}

        {user.guest && <GuestNotice onProfile />}

        {profile.isPending ? (
          <ProfileSkeleton />
        ) : profile.isError ? (
          <div className="border-t border-paper-300 pt-8 text-center">
            <p className="text-stone-700">{messageFor(profile.error)}</p>
            <PrimaryButton type="button" onClick={() => void profile.refetch()} className="mt-3">
              Try again
            </PrimaryButton>
          </div>
        ) : (
          <>
            <ProfileHeader user={user} profile={profile.data} />
            {/* Keyed by user so switching accounts never shows the previous draft. */}
            <ProfileForm key={user.id} user={user} profile={profile.data} />
            <SecuritySection user={user} />
            <CommunicationSection user={user} />
          </>
        )}
      </div>
      {profile.isSuccess && <SectionNav />}
    </div>
  )
}

const SECTIONS = [
  { id: 'photo', label: 'Photo' },
  { id: 'personal', label: 'Personal details' },
  { id: 'emergency', label: 'Emergency contact' },
  { id: 'experience', label: 'Trekking experience' },
  { id: 'health', label: 'Health & fitness' },
  { id: 'food', label: 'Food & gear' },
  { id: 'security', label: 'Sign-in & security' },
  { id: 'communication', label: 'Communication' },
]

/** Wide screens only: sticky jump list whose marker follows the section being read. */
function SectionNav() {
  const [active, setActive] = useState(SECTIONS[0].id)

  useEffect(() => {
    // Current = the last section whose top has passed a line 30% down the viewport;
    // at the very bottom of the page the last section wins even if it's short.
    let frame = 0
    const update = () => {
      frame = 0
      const line = window.innerHeight * 0.3
      const atBottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4
      let current = SECTIONS[0].id
      for (const s of SECTIONS) {
        const el = document.getElementById(s.id)
        if (el && el.getBoundingClientRect().top <= line) current = s.id
      }
      setActive(atBottom ? SECTIONS[SECTIONS.length - 1].id : current)
    }
    const onScroll = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onScroll)
    return () => {
      cancelAnimationFrame(frame)
      window.removeEventListener('scroll', onScroll)
      window.removeEventListener('resize', onScroll)
    }
  }, [])

  const index = Math.max(0, SECTIONS.findIndex((s) => s.id === active))

  return (
    <nav aria-label="Profile sections" className="hidden lg:block">
      <div className="sticky top-28">
        <p className="text-[0.7rem] font-medium tracking-[0.12em] text-stone-500 uppercase">On this page</p>
        <div className="relative mt-3 border-l border-paper-300">
          <span
            aria-hidden="true"
            className="absolute -left-px h-8 w-0.5 bg-pine-600 transition-transform duration-300 ease-[cubic-bezier(0.2,0.8,0.2,1)]"
            style={{ transform: `translateY(${index * 2}rem)` }}
          />
          <ul>
            {SECTIONS.map((s) => (
              <li key={s.id}>
                <a
                  href={`#${s.id}`}
                  aria-current={s.id === active ? 'location' : undefined}
                  onClick={(e) => {
                    e.preventDefault()
                    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
                    document.getElementById(s.id)?.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' })
                  }}
                  className={`flex h-8 items-center pl-4 text-[0.8rem] transition-colors ${
                    s.id === active ? 'text-stone-900' : 'text-stone-500 hover:text-stone-800'
                  }`}
                >
                  {s.label}
                </a>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </nav>
  )
}

function ProfileSkeleton() {
  return (
    <div className="space-y-5 border-t border-paper-300 pt-8" aria-busy="true" aria-label="Loading profile">
      {[56, 48, 48, 48].map((h, i) => (
        <div key={i} className="animate-pulse rounded-(--card-radius) bg-paper-200" style={{ height: h }} />
      ))}
    </div>
  )
}
