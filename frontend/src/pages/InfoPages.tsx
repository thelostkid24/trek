import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { FaqList } from '../components/FaqList.tsx'
import { BUSINESS } from '../lib/business.ts'
import { FAQS } from '../lib/faqs.ts'
import { SITE_LINKS } from '../lib/siteLinks.ts'

/** Plain text pages linked from the header and footer, in the landing page's style. */
function InfoPage({ title, intro, children }: { title: string; intro: string; children?: ReactNode }) {
  return (
    <div className="min-h-[calc(100dvh-4rem)] bg-paper-50 font-plex text-ink-900">
      <section className="mx-auto max-w-[44rem] px-5 py-16 sm:px-10 sm:py-20">
        <h1 className="font-serif text-4xl font-light tracking-tight sm:text-[2.8rem]">{title}</h1>
        <p className="mt-3 text-base leading-relaxed text-ink-700">{intro}</p>
        {children}
      </section>
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <>
      <h2 className="mt-10 font-serif text-2xl">{title}</h2>
      <div className="mt-2 space-y-3 text-sm leading-relaxed text-ink-700">{children}</div>
    </>
  )
}

const linkClass = 'font-medium text-pine-600 hover:text-pine-700'

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
      intro="You can cancel a paid booking from its page in My treks, any time before the start date. The whole booking is cancelled together."
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
      <Section title="Why we exist">
        <p>
          Most trek bookings are a gamble: big groups, a guide you meet on the day, and a departure that is cancelled if
          not enough people sign up. We think the people who know the mountains best should lead, and that a booked trek
          should simply happen.
        </p>
      </Section>
      <Section title="What that means for you">
        <ul className="list-disc space-y-2 pl-5">
          <li>At most ten trekkers per departure, with one named guide you can read about before you book.</li>
          <li>Once you’ve paid, your trek runs. We never cancel for low numbers.</li>
          <li>
            We only cancel for weather, permits, safety or a guide with no substitute, and then you get a full refund.
          </li>
          <li>Most of what you pay goes to the guide who leads you.</li>
        </ul>
      </Section>
      <Section title="Who we are">
        <p>
          The Empty Valley is run by {BUSINESS.legalName}, {BUSINESS.address}. Reach us any time through the{' '}
          <Link to={SITE_LINKS.contact} className={linkClass}>
            contact page
          </Link>
          .
        </p>
      </Section>
    </InfoPage>
  )
}

export function ContactPage() {
  return (
    <InfoPage
      title="Contact"
      intro="Have a question about a booking? Open it from My treks — your booking page has your guide, dates and payment details in one place."
    >
      <dl className="mt-8 grid gap-4 rounded-(--card-radius) border border-paper-300 bg-paper-50 p-6 text-sm sm:grid-cols-[10rem_1fr]">
        <dt className="text-ink-400">Email</dt>
        <dd>
          <a href={`mailto:${BUSINESS.supportEmail}`} className={linkClass}>
            {BUSINESS.supportEmail}
          </a>
        </dd>
        <dt className="text-ink-400">Phone / WhatsApp</dt>
        <dd>
          <a href={`tel:${BUSINESS.supportPhone.replace(/\s/g, '')}`} className={linkClass}>
            {BUSINESS.supportPhone}
          </a>
        </dd>
        <dt className="text-ink-400">Hours</dt>
        <dd className="text-ink-700">{BUSINESS.supportHours}</dd>
        <dt className="text-ink-400">Address</dt>
        <dd className="text-ink-700">
          {BUSINESS.legalName}
          <br />
          {BUSINESS.address}
        </dd>
      </dl>
      <p className="mt-6 text-sm text-ink-700">
        Refunds follow our{' '}
        <Link to={SITE_LINKS.cancellations} className={linkClass}>
          cancellation policy
        </Link>
        . Complaints about personal data go to our grievance officer (see the{' '}
        <Link to={SITE_LINKS.privacy} className={linkClass}>
          privacy policy
        </Link>
        ).
      </p>
    </InfoPage>
  )
}

export function TermsPage() {
  return (
    <InfoPage
      title="Terms of use"
      intro={`These terms apply when you use The Empty Valley or book a trek with us. The Empty Valley is operated by ${BUSINESS.legalName}. Last updated ${BUSINESS.lastUpdated}.`}
    >
      <Section title="Bookings and payment">
        <p>
          A booking holds your seats for 10 minutes while you pay. It is confirmed only once the payment succeeds. Prices
          are per seat, in Indian rupees, and include applicable taxes unless stated otherwise.
        </p>
        <p>
          Payments are processed by Razorpay. We never see or store your card details. Once your booking is confirmed, the
          departure runs. We do not cancel for low numbers.
        </p>
      </Section>
      <Section title="Cancellations and refunds">
        <p>
          You may cancel before the start date under our{' '}
          <Link to={SITE_LINKS.cancellations} className={linkClass}>
            cancellation policy
          </Link>
          . The tiers in force when you paid apply to your booking. If we cancel a departure for weather, permits, safety or
          a guide with no substitute, every paid booking is refunded in full. Refunds go back to the original payment method.
        </p>
      </Section>
      <Section title="Your responsibilities">
        <p>
          Trekking carries real risk. You confirm that every traveller is 18 or older, fit for the trek’s difficulty, and
          has shared any medical condition that matters. Follow your guide’s safety instructions. The guide may stop anyone
          from continuing when it isn’t safe, and that alone doesn’t earn a refund.
        </p>
      </Section>
      <Section title="Accounts">
        <p>
          Keep your sign-in details to yourself. You’re responsible for bookings made from your account. We may suspend
          accounts used for fraud or abuse.
        </p>
      </Section>
      <Section title="Liability">
        <p>
          To the extent the law allows, our liability for any booking is limited to the amount you paid for it. Nothing
          in these terms limits rights you have under Indian consumer law.
        </p>
      </Section>
      <Section title="Law and disputes">
        <p>
          These terms are governed by the laws of India. Courts in Mumbai have jurisdiction. Questions:{' '}
          <a href={`mailto:${BUSINESS.supportEmail}`} className={linkClass}>
            {BUSINESS.supportEmail}
          </a>
          .
        </p>
      </Section>
    </InfoPage>
  )
}

export function PrivacyPage() {
  return (
    <InfoPage
      title="Privacy policy"
      intro={`How ${BUSINESS.legalName} (“The Empty Valley”) collects and uses your personal data, under India’s Digital Personal Data Protection Act, 2023. Last updated ${BUSINESS.lastUpdated}.`}
    >
      <Section title="What we collect">
        <ul className="list-disc space-y-2 pl-5">
          <li>Account: name, email, mobile number, password (stored only as a hash), or your Google sign-in.</li>
          <li>Bookings: contact details and, for each traveller, name, date of birth, gender and optionally a phone number.</li>
          <li>
            Trek safety (only if you provide it): emergency contact, blood group, medical notes, allergies, diet, height,
            weight and trekking experience.
          </li>
          <li>Payments: status and method from Razorpay. Card numbers never reach us.</li>
          <li>
            How you found us: the campaign tags and ad click ids in the link you arrived by, the website that sent you,
            the first page you saw, when, and whether you were on a phone, tablet or computer. If you choose to tell us,
            how you heard about us. We keep this with your account and your bookings, and remember it in your browser
            until you sign up or book.
          </li>
          <li>Your choices about trek offers by email or WhatsApp, and when you made them.</li>
          <li>When you last used your account.</li>
          <li>Technical: IP address and request logs, kept for security and abuse prevention.</li>
        </ul>
      </Section>
      <Section title="Why we use it">
        <p>
          To run your booking, keep you safe on the trail (your guide sees the safety details for their departure), process
          payments and refunds, contact you about your trip, and meet legal and tax obligations. We use how you found us,
          in totals only, to learn which channels bring trekkers and where our booking steps lose people. We send trek
          offers only if you tick the box for that channel. Your safety details are never used for marketing or analysis.
          We don’t sell your data or use it for third-party advertising.
        </p>
      </Section>
      <Section title="Who we share it with">
        <p>
          Your trek’s guide; Razorpay (payments); Amazon Web Services in Mumbai (hosting, storage and email); our SMS provider
          (sign-in codes); and authorities where the law requires it. Your data is stored in India.
        </p>
      </Section>
      <Section title="How long we keep it">
        <p>
          Account data is kept while your account is open. Booking and payment records are kept for 8 years for tax and
          accounting. Medical notes are kept only until you delete them or close your account.
        </p>
      </Section>
      <Section title="Your rights">
        <p>
          You can see and correct most of your data on your profile, and switch trek offers on or off there at any time.
          You can also ask us for a summary of your data, to
          correct or erase it, or to withdraw consent, by writing to our grievance officer. We reply within 30 days. If
          you’re not satisfied, you may complain to the Data Protection Board of India.
        </p>
      </Section>
      <Section title="Grievance officer">
        <p>
          {BUSINESS.grievanceOfficer},{' '}
          <a href={`mailto:${BUSINESS.grievanceEmail}`} className={linkClass}>
            {BUSINESS.grievanceEmail}
          </a>
          , {BUSINESS.address}.
        </p>
      </Section>
    </InfoPage>
  )
}
