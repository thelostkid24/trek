import { useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { FaqList } from '../components/FaqList.tsx'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { BUSINESS } from '../lib/business.ts'
import { FAQS } from '../lib/faqs.ts'
import { SITE_LINKS } from '../lib/siteLinks.ts'
import { Seo } from '../components/Seo.tsx'

/** Plain text pages linked from the header and footer, in the landing page's style. */
function InfoPage({ title, intro, path, wide = false, children }: { title: string; intro?: string; path: string; wide?: boolean; children?: ReactNode }) {
  return (
    <div className="min-h-[calc(100dvh-4rem)] bg-paper-50 font-plex text-ink-900">
      <Seo title={title} description={intro} path={path} />
      <section className={`mx-auto px-5 py-16 sm:px-10 sm:py-20 ${wide ? 'max-w-5xl' : 'max-w-[44rem]'}`}>
        <h1 className="font-serif text-4xl font-light tracking-tight sm:text-[2.8rem]">{title}</h1>
        {intro && <p className="mt-3 max-w-[40rem] text-base leading-relaxed text-ink-700">{intro}</p>}
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
    <InfoPage title="FAQs" path={SITE_LINKS.faqs} intro="Everything trekkers ask us before they book.">
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
      path={SITE_LINKS.cancellations}
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

const VISION_TABS = [
  { key: 'story', label: 'How it started' },
  { key: 'founders', label: 'Founders' },
] as const

/**
 * Sample founders until the real ones send their photos and words: `sample` puts a "Sample profile" tag on the
 * card, so nobody reads them as real. `photo` is a path under public/ (a portrait, roughly 4:5); a ridgeline with
 * initials stands in until there is one. `story` is two or three lines.
 * TODO: real names, photos, quotes and stories before launch, then drop `sample`.
 */
const FOUNDERS = [
  {
    name: 'Aditya Rawat',
    sample: true,
    photo: null as string | null,
    role: 'Co-founder',
    looksAfter: 'Guides, routes and safety',
    home: 'Uttarkashi',
    onTrailSince: 2012,
    story:
      'Grew up two valleys from Sankri and spent years carrying loads for big trek companies. Every guide on The Empty Valley is someone he has walked with himself, and most of the year he is on the trail, not at a desk.',
  },
  {
    name: 'Nisha Menon',
    sample: true,
    photo: null as string | null,
    role: 'Co-founder',
    looksAfter: 'Bookings and trekker care',
    home: 'Mumbai',
    onTrailSince: 2016,
    story:
      'Her first Himalayan trek was cancelled two days out for low numbers. She left a desk job to build the booking side: the guide’s name before you pay, nothing added at checkout, and a paid date that always runs.',
  },
]

type Founder = (typeof FOUNDERS)[number]

/** Initials for the stand-in portrait. */
const initials = (name: string) =>
  name
    .split(' ')
    .filter(Boolean)
    .map((w) => w[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

/** The founder's portrait, or a night-green ridgeline with their initials until there is one. */
function Portrait({ founder: f, className = '' }: { founder: Founder; className?: string }) {
  if (f.photo) return <img src={f.photo} alt={`${f.name}'s photo`} className={`object-cover ${className}`} />
  return (
    <div className={`relative isolate flex items-center justify-center overflow-hidden bg-brand-900 ${className}`} aria-hidden="true">
      <Ridgeline className="absolute inset-x-0 bottom-0 -z-10 h-2/3 w-full opacity-60" />
      <span className="font-serif text-4xl font-light text-white/90">{initials(f.name)}</span>
    </div>
  )
}

/**
 * Founders in two panels: on the left an "Our founders" list of photo cards (the picked one outlined, with a tick);
 * on the right just the words: name, role, "About" and their story. On phones the list sits above, side by side.
 */
function Founders() {
  const [picked, setPicked] = useState(0)
  const f = FOUNDERS[picked]
  return (
    <div className="mt-10 grid overflow-hidden rounded-3xl bg-white shadow-sm ring-1 ring-paper-300 md:grid-cols-[17rem_1fr]">
      <div className="bg-paper-100 p-5 sm:p-6">
        <h2 className="font-serif text-2xl">Our founders</h2>
        <div role="tablist" aria-label="Founders" className="mt-5 grid grid-cols-2 gap-3 md:grid-cols-1 md:gap-4">
          {FOUNDERS.map((x, i) => {
            const on = i === picked
            return (
              <button
                key={x.name}
                type="button"
                role="tab"
                aria-selected={on}
                onClick={() => setPicked(i)}
                className={`relative rounded-2xl bg-white p-2 text-left transition ${
                  on ? 'ring-2 ring-brand-700' : 'ring-1 ring-paper-300 hover:ring-stone-400'
                }`}
              >
                <Portrait founder={x} className="aspect-[5/4] w-full rounded-xl" />
                {on && (
                  <span className="absolute top-4 right-4 flex size-6 items-center justify-center rounded-full bg-brand-700 text-xs text-white" aria-hidden="true">
                    ✓
                  </span>
                )}
                <span className="block px-2 pt-3 pb-1 font-medium text-ink-900">{x.name}</span>
                <span className="block px-2 pb-1 text-xs font-medium text-laterite-600">{x.role}</span>
              </button>
            )
          })}
        </div>
      </div>

      <article role="tabpanel" aria-label={f.name} className="min-w-0 p-5 sm:p-8">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-serif text-3xl font-light sm:text-4xl">{f.name}</h3>
          {f.sample && (
            <span className="rounded-full bg-paper-200 px-2 py-0.5 text-[0.65rem] font-medium text-ink-700">Sample profile</span>
          )}
        </div>
        <p className="mt-1 font-medium text-laterite-600">
          {f.role} · {f.looksAfter}
        </p>

        <h4 className="mt-6 flex items-center gap-2 text-lg font-semibold text-ink-900">
          <svg viewBox="0 0 24 24" className="size-5 text-laterite-600" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true">
            <path d="M12 6.5C10 5 7 4.5 3.5 5v13c3.5-.5 6.5 0 8.5 1.5 2-1.5 5-2 8.5-1.5V5C17 4.5 14 5 12 6.5Zm0 0V19.5" />
          </svg>
          About
        </h4>
        <p className="mt-3 max-w-[40rem] text-base leading-relaxed text-ink-700">{f.story}</p>
        <p className="mt-4 text-sm text-ink-700">
          Based in {f.home} · on the trail since {f.onTrailSince}
        </p>
      </article>
    </div>
  )
}

export function VisionPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'founders' ? 'founders' : 'story'
  return (
    <InfoPage
      wide
      title="Our vision"
      path={SITE_LINKS.vision}
    >
      <div role="tablist" aria-label="Our vision" className="mt-8 inline-flex rounded-full bg-paper-200 p-1">
        {VISION_TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            aria-selected={tab === t.key}
            onClick={() => setParams(t.key === 'story' ? {} : { tab: t.key }, { replace: true })}
            className={`rounded-full px-5 py-2 text-sm font-medium transition ${
              tab === t.key ? 'bg-white text-ink-900 shadow-sm' : 'text-ink-700 hover:text-ink-900'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'story' ? (
        <div role="tabpanel" className="max-w-[40rem]">
          <Section title="How it started">
            <p>
              I was diagnosed with cancer. I’m not going to make that sound like more than it was, or less: it
              happened, I got through it, and when it was over I did something I had never seriously considered before.
              I signed up for a Basic Mountaineering Course. I don’t know exactly what I was looking for. But somewhere
              in those weeks, carrying a load uphill in weather I’d have avoided a year earlier, I understood something I
              hadn’t before: that being alive is a thing you can either use or not use, there’s no in-between, and
              nobody hands you the difference. I fell in love with the mountains in the simplest way possible. I just
              wanted to keep going back. Then I did my Advanced Mountaineering Course at HMI.
            </p>
            <p>
              What I didn’t expect was what I’d notice while I was there. I kept meeting guides — local men from the
              valleys, certified, some of them better on a mountain than anyone I’d ever watched — and most of them were
              either out of work for months at a stretch or working for a trekking company that paid them very little
              for a great deal of work. They were the ones carrying the responsibility: reading the weather, watching
              the slow trekker, deciding when to turn a group around. And then the trek would end, the trekkers would go
              home, and they’d write reviews about the company. Not the man. The company. That’s the thing I want to
              change — not the pay alone, though that matters, but the recognition. The work on a mountain is done by a
              person, and that person should be visible.
            </p>
            <p>
              That’s when I met Vikhilesh, my co-founder — the person who turned the idea into technology and made this
              platform possible. Together, we built The Empty Valley the other way round. The guides aren’t our staff
              here; they run their own departures on this platform. They choose their dates, they lead with their own
              local teams, and they keep most of what you pay. We do the part they shouldn’t have to — the bookings, the
              verification, the website, the questions at eleven at night — and they do the mountain. And on our side,
              you choose them. Every date on this site carries a name and a face, and you can read who he is, where
              he’s from, how many seasons he’s led, and what people who walked with him have said, before you pay.{' '}
              <strong className="font-semibold text-ink-900">
                As far as we know, this is the first time in Indian trekking that a trekker gets to choose the guide
                rather than the company.
              </strong>
            </p>
            <p>
              That difference is smaller than it sounds and bigger than it looks. When you book a company, you are
              buying a promise from an organisation: if the trek goes well the brand gets the credit, and if it goes
              badly nobody in particular is answerable. The person who actually took you up the mountain is
              interchangeable, and he knows it, which is exactly why he’s paid the way he is. When you choose a guide,
              two things change at once. You know who you’re going with before you leave home, which is what almost
              everyone is quietly anxious about anyway. And he builds something of his own — a record, a reputation,
              people who come back and ask for him by name. So his next season depends on how he treated you on this
              one. That’s the whole idea. You should know who you’re going up there with, and he should get the credit
              for taking you.
            </p>
          </Section>
        </div>
      ) : (
        <Founders />
      )}
    </InfoPage>
  )
}

export function ContactPage() {
  return (
    <InfoPage
      title="Contact"
      path={SITE_LINKS.contact}
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
      path={SITE_LINKS.terms}
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
          accounts used for fraud or abuse. How we handle your data, including when you sign in with Google, is in our{' '}
          <Link to={SITE_LINKS.privacy} className={linkClass}>
            privacy policy
          </Link>
          .
        </p>
      </Section>
      <Section title="Reviews">
        <p>
          After a trek you can rate your guide and write a review. Reviews are shown publicly with your first name, and you
          can edit yours later. Keep them honest and about the trek. We may remove reviews that are abusive, share other
          people’s personal details, or have nothing to do with the trek.
        </p>
      </Section>
      <Section title="Liability">
        <p>
          To the extent the law allows, our liability for any booking is limited to the amount you paid for it. Nothing
          in these terms limits rights you have under Indian consumer law.
        </p>
      </Section>
      <Section title="Changes to these terms">
        <p>
          We may update these terms and will change the date at the top when we do. Changes never apply to a booking you’ve
          already paid for.
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
      path={SITE_LINKS.privacy}
      intro={`How ${BUSINESS.legalName} (“The Empty Valley”) collects and uses your personal data, under India’s Digital Personal Data Protection Act, 2023. Last updated ${BUSINESS.lastUpdated}.`}
    >
      <Section title="What we collect">
        <ul className="list-disc space-y-2 pl-5">
          <li>
            Account: name, email, mobile number, password (stored only as a hash), and a profile photo if you upload one.
          </li>
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
          <li>Reviews you write after a trek, shown publicly with your first name.</li>
          <li>Technical: IP address and request logs, kept for security and abuse prevention.</li>
        </ul>
      </Section>
      <Section title="Signing in with Google">
        <p>
          If you choose “Sign in with Google”, Google sends us your name, email address, whether Google has verified that
          email, and your Google account id. We ask for nothing else: no contacts, calendar, files or
          other Google data, and we never see your Google password.
        </p>
        <p>
          We use this only to create your account or sign you in, and to link Google to an existing account with the same
          verified email. It is not shared with anyone, not used for advertising, and deleted along with your account. You
          can remove our access at any time from your Google Account’s security settings; your account here stays, and you
          can sign in another way.
        </p>
      </Section>
      <Section title="Cookies and browser storage">
        <p>
          We set one cookie, which keeps you signed in. It is only sent to our own servers and can’t be read by page
          scripts. Your browser also remembers your light or dark theme, and the link you first arrived by until you sign up
          or book. We don’t use advertising or third-party tracking cookies. Our pages load fonts from Google Fonts, which
          sees your IP address when your browser fetches them.
        </p>
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
          (sign-in codes); Google (only if you sign in with Google); and authorities where the law requires it. Your data
          is stored in India.
        </p>
      </Section>
      <Section title="How long we keep it">
        <p>
          Account data is kept while your account is open. Booking and payment records are kept for 8 years for tax and
          accounting. Medical notes are kept only until you delete them or ask us to close your account.
        </p>
      </Section>
      <Section title="Children">
        <p>Our treks and accounts are for people 18 and older. We don’t knowingly collect data about children.</p>
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
      <Section title="Changes to this policy">
        <p>
          When we change this policy we update the date at the top. If a change affects how we use data you’ve already given
          us, we’ll tell you by email before it applies.
        </p>
      </Section>
    </InfoPage>
  )
}
