import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { FaqList } from '../components/FaqList.tsx'
import { FAQS } from '../lib/faqs.ts'
import { SITE_LINKS } from '../lib/siteLinks.ts'

/** Plain text pages linked from the header and footer, in the landing page's style. */
function InfoPage({ title, intro, children }: { title: string; intro: string; children?: ReactNode }) {
  return (
    <div className="min-h-[calc(100dvh-4rem)] bg-paper-100 font-plex text-ink-900">
      <section className="mx-auto max-w-[44rem] px-5 py-16 sm:px-10 sm:py-20">
        <h1 className="font-serif text-4xl tracking-tight sm:text-[2.8rem]">{title}</h1>
        <p className="mt-3 text-base leading-relaxed text-ink-700">{intro}</p>
        {children}
      </section>
    </div>
  )
}

function ComingSoon() {
  return (
    <p className="mt-8 rounded-sm border border-paper-300 bg-paper-50 p-6 text-sm text-ink-700">
      We’re still writing this page. Meanwhile, the{' '}
      <Link to={SITE_LINKS.faqs} className="font-medium text-pine-600 hover:text-pine-700">
        FAQs
      </Link>{' '}
      cover the most common questions, or{' '}
      <a href="/#treks" className="font-medium text-pine-600 hover:text-pine-700">
        browse the treks
      </a>
      .
    </p>
  )
}

export function FaqsPage() {
  return (
    <InfoPage title="FAQs" intro="Everything trekkers ask us before they book.">
      <FaqList items={FAQS} />
      <p className="mt-6 text-sm text-ink-700">
        Refund details are in the{' '}
        <Link to={SITE_LINKS.cancellations} className="font-medium text-pine-600 hover:text-pine-700">
          cancellation policy
        </Link>
        .
      </p>
    </InfoPage>
  )
}

// Mirrors app.bookings.refund-tiers in backend application.yml — keep the two in step.
const REFUND_TIERS = [
  ['15 days or more before the start date', '90% refunded'],
  ['7 to 14 days before', '50% refunded'],
  ['1 to 6 days before', 'No refund — your seats are freed for someone else'],
  ['On or after the start date', 'Can’t be cancelled'],
]

export function CancellationsPage() {
  return (
    <InfoPage
      title="Cancellations & refunds"
      intro="You can cancel a paid booking from its page in My trips, any time before the start date. The whole booking is cancelled together."
    >
      <table className="mt-8 w-full border-collapse text-left text-sm">
        <thead>
          <tr className="border-b border-paper-300 text-[0.7rem] tracking-[0.08em] text-ink-400 uppercase">
            <th className="py-3 pr-4 font-medium">When you cancel</th>
            <th className="py-3 font-medium">What you get back</th>
          </tr>
        </thead>
        <tbody>
          {REFUND_TIERS.map(([when, refund]) => (
            <tr key={when} className="border-b border-paper-300">
              <td className="py-3 pr-4 text-ink-900">{when}</td>
              <td className="py-3 text-ink-700">{refund}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="mt-6 text-sm leading-relaxed text-ink-700">
        The policy in force when you paid is the one that applies to your booking. Before you confirm a cancellation, the
        booking page shows the exact amount you’ll get back.
      </p>
      <h2 className="mt-10 font-serif text-2xl">If we cancel</h2>
      <p className="mt-2 text-sm leading-relaxed text-ink-700">
        We only cancel a departure for weather, a denied permit, a guide who can’t go and has no substitute, or safety —
        never because it didn’t fill up. When we do, every paid booking is refunded in full.
      </p>
    </InfoPage>
  )
}

export function VisionPage() {
  return (
    <InfoPage
      title="Our vision"
      intro="Fair-trade, micro-batch trekking: small groups of up to ten, each led by a named local guide, on departures that run whether or not they fill."
    >
      <ComingSoon />
    </InfoPage>
  )
}

export function ContactPage() {
  return (
    <InfoPage
      title="Contact"
      intro="Have a question about a booking? Open it from My trips — your booking page has your guide, dates and payment details in one place."
    >
      <ComingSoon />
    </InfoPage>
  )
}

export function LeadATrekPage() {
  return (
    <InfoPage
      title="Lead a trek"
      intro="Sahyātri is built around local guides. Guide sign-up is on its way — we’re onboarding our first guides directly for now."
    >
      <ComingSoon />
    </InfoPage>
  )
}
