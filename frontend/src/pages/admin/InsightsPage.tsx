import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { HEARD_FROM_OPTIONS } from '../../analytics/attribution.ts'
import { getInsights, type CountRow, type Insights, type SourceRow } from '../../api/insights.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { dayLabel, rupees } from '../../lib/format.ts'

// Admin Insights (docs/TRD.md §7.15). One series per chart, so one mark color (brand-500) and no legends;
// every value is also printed beside its mark or in a table.

const WINDOWS = [7, 30, 90] as const

const KEY_LABELS: Record<string, string> = {
  ...Object.fromEntries(HEARD_FROM_OPTIONS.map((o) => [o.value, o.label])),
  NO_ANSWER: 'No answer',
  EMAIL: 'Email & password',
  PHONE: 'Mobile OTP',
  GOOGLE: 'Google',
  GUEST_CHECKOUT: 'Guest checkout',
  MOBILE: 'Mobile',
  TABLET: 'Tablet',
  DESKTOP: 'Desktop',
  UNKNOWN: 'Before tracking',
  NETBANKING: 'Netbanking',
  WALLET: 'Wallet',
  CARD: 'Card',
}

const SOURCE_LABELS: Record<string, string> = { direct: 'Direct', unknown: 'Before tracking' }

const count = (n: number) => n.toLocaleString('en-IN')
const percent = (bps: number | null) => (bps === null ? '—' : `${Math.round(bps / 10) / 10}%`)
const share = (part: number, whole: number) => (whole === 0 ? '—' : `${Math.round((part * 1000) / whole) / 10}%`)

export function InsightsPage() {
  const { withAuth } = useAuth()
  const [days, setDays] = useState<number>(30)
  const insights = useQuery({
    queryKey: ['admin-insights', days],
    queryFn: () => withAuth((token) => getInsights(token, days)),
    placeholderData: keepPreviousData,
  })

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-stone-600">
          Computed live from bookings and accounts. Sources and devices cover accounts made after tracking began.
        </p>
        <WindowPicker value={days} onChange={setDays} />
      </div>
      {insights.isPending ? (
        <Loading />
      ) : insights.isError ? (
        <ErrorNote error={insights.error} />
      ) : (
        <Dashboard data={insights.data} />
      )}
    </>
  )
}

function WindowPicker({ value, onChange }: { value: number; onChange: (days: number) => void }) {
  return (
    <div className="inline-flex rounded-full bg-stone-100 p-1" role="group" aria-label="Time window">
      {WINDOWS.map((d) => (
        <button
          key={d}
          type="button"
          aria-pressed={value === d}
          onClick={() => onChange(d)}
          className={`rounded-full px-3 py-1.5 text-sm font-medium ${
            value === d ? 'bg-white text-stone-900 shadow-sm' : 'text-stone-600 hover:text-stone-900'
          }`}
        >
          {d} days
        </button>
      ))}
    </div>
  )
}

function Dashboard({ data }: { data: Insights }) {
  const h = data.headline
  const firstStep = data.funnel[0]?.count ?? 0

  return (
    <>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
        <StatTile label="New accounts" value={count(h.new_accounts)} />
        <StatTile label="Bookings confirmed" value={count(h.bookings_confirmed)} />
        <StatTile label="Gross bookings" value={rupees(h.gross_paise)} />
        <StatTile label="Holds that paid" value={percent(h.hold_to_paid_bps)} note={`${count(h.bookings_held)} holds`} />
        <StatTile label="Average group" value={h.avg_group_size === null ? '—' : `${h.avg_group_size} seats`} />
        <StatTile label="Cancellations" value={count(h.cancellations)} />
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <Panel title="New accounts per day">
          <DailyColumns points={data.daily.map((d) => ({ date: d.date, value: d.accounts }))} unit="accounts" />
        </Panel>
        <Panel title="Bookings confirmed per day">
          <DailyColumns points={data.daily.map((d) => ({ date: d.date, value: d.confirmed }))} unit="bookings" />
        </Panel>
      </div>

      <Panel title="Funnel: accounts made in this window">
        <HBars
          rows={data.funnel.map((step, i) => ({
            key: step.key,
            label: step.label,
            value: step.count,
            note: i === 0 ? undefined : `${share(step.count, firstStep)} of sign-ups`,
          }))}
        />
      </Panel>

      <Panel title="Sources">
        <SourceTable rows={data.sources} labelHeader="Source" empty="No accounts or bookings in this window." />
      </Panel>

      <Panel title="Campaigns">
        <SourceTable rows={data.campaigns} labelHeader="Campaign" empty="No tagged campaigns in this window." />
      </Panel>

      <div className="grid gap-5 md:grid-cols-2">
        <Panel title="How they heard about us">
          <HBars rows={countRows(data.heard_from)} empty="No new accounts in this window." />
        </Panel>
        <Panel title="Sign-up method">
          <HBars rows={countRows(data.signup_methods)} empty="No new accounts in this window." />
        </Panel>
        <Panel title="Device at sign-up">
          <HBars rows={countRows(data.devices)} empty="No new accounts in this window." />
        </Panel>
        <Panel title="Marketing reach">
          <p className="text-sm text-stone-600">Active trekker accounts that agreed to trek offers.</p>
          <HBars
            rows={[
              { key: 'email', label: 'Email', value: data.marketing_reach.email, note: share(data.marketing_reach.email, data.marketing_reach.accounts) },
              { key: 'whatsapp', label: 'WhatsApp', value: data.marketing_reach.whatsapp, note: share(data.marketing_reach.whatsapp, data.marketing_reach.accounts) },
            ]}
            max={data.marketing_reach.accounts}
          />
          <p className="mt-2 text-xs text-stone-500">Out of {count(data.marketing_reach.accounts)} accounts.</p>
        </Panel>
      </div>

      <Panel title="Payments">
        <div className="grid grid-cols-3 gap-3">
          <StatTile label="Success rate" value={percent(data.payments.success_bps)} />
          <StatTile label="Paid" value={count(data.payments.paid)} />
          <StatTile label="Failed" value={count(data.payments.failed)} />
        </div>
        <div className="mt-5 grid gap-5 md:grid-cols-2">
          <div>
            <h3 className="text-sm font-medium text-stone-800">Paid by</h3>
            <div className="mt-2">
              <HBars rows={countRows(data.payments.methods)} empty="No paid orders." />
            </div>
          </div>
          <div>
            <h3 className="text-sm font-medium text-stone-800">Why payments failed</h3>
            <div className="mt-2">
              <HBars rows={countRows(data.payments.failures)} empty="No failed payments." />
            </div>
          </div>
        </div>
      </Panel>

      <Panel title="Treks">
        <Table
          head={['Trek', 'Bookings', 'Seats', 'Gross', 'Rating']}
          empty="No bookings confirmed in this window."
          rows={data.treks.map((t) => [
            <Link key="n" to={`/treks/${t.slug}`} className="font-medium text-brand-800 hover:underline">
              {t.name}
            </Link>,
            count(t.confirmed_bookings),
            count(t.seats),
            rupees(t.gross_paise),
            t.avg_rating === null ? '—' : `${t.avg_rating} (${t.reviews})`,
          ])}
        />
      </Panel>

      <Panel title="Next 60 days">
        <Table
          head={['Starts', 'Trek', 'Guide', 'Filled']}
          empty="No published departures in the next 60 days."
          rows={data.upcoming.map((d) => [
            dayLabel(d.start_date),
            d.track_name,
            d.guide_name ?? '—',
            <FillMeter key="f" taken={d.seats_taken} size={d.max_group_size} />,
          ])}
        />
      </Panel>

      <Panel title="Guides (all time)">
        <Table
          head={['Guide', 'Treks led', 'Avg fill', 'Rating']}
          empty="No guides yet."
          rows={data.guides.map((g) => [
            g.name ?? '—',
            count(g.departures_completed),
            percent(g.avg_fill_bps),
            g.avg_rating === null ? '—' : `${g.avg_rating} (${g.reviews})`,
          ])}
        />
      </Panel>
    </>
  )
}

const countRows = (rows: CountRow[]) =>
  rows.map((r) => ({ key: r.key, label: KEY_LABELS[r.key] ?? r.key, value: r.count }))

function StatTile({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div className="rounded-2xl border border-stone-200 bg-white px-4 py-3">
      <p className="text-xs text-stone-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-stone-900">{value}</p>
      {note && <p className="text-xs text-stone-500">{note}</p>}
    </div>
  )
}

/** Columns per day. Hovering or focusing a day names it and its value above the plot. */
function DailyColumns({ points, unit }: { points: { date: string; value: number }[]; unit: string }) {
  const [active, setActive] = useState<number | null>(null)
  const max = Math.max(1, ...points.map((p) => p.value))
  const total = points.reduce((sum, p) => sum + p.value, 0)
  const shown = active === null ? null : points[active]

  return (
    <div>
      <p className="h-5 text-sm text-stone-600" aria-live="polite">
        {shown ? (
          <>
            <span className="font-medium text-stone-900">{count(shown.value)}</span> on {dayLabel(shown.date)}
          </>
        ) : (
          <>
            <span className="font-medium text-stone-900">{count(total)}</span> {unit} in total
          </>
        )}
      </p>
      <div className="relative mt-3">
        <span className="absolute -top-2 right-0 text-xs text-stone-400 tabular-nums">{count(max)}</span>
        <div className="flex h-28 items-end gap-0.5 border-b border-stone-200" onMouseLeave={() => setActive(null)}>
          {points.map((p, i) => (
            <button
              key={p.date}
              type="button"
              aria-label={`${dayLabel(p.date)}: ${p.value} ${unit}`}
              onMouseEnter={() => setActive(i)}
              onFocus={() => setActive(i)}
              onBlur={() => setActive(null)}
              className="flex h-full min-w-0 flex-1 items-end justify-center outline-none focus-visible:bg-stone-100"
            >
              <span
                className={`block w-full max-w-6 rounded-t ${active === i ? 'bg-brand-700' : 'bg-brand-500'}`}
                style={{ height: p.value === 0 ? 0 : `max(2px, ${(p.value / max) * 100}%)` }}
              />
            </button>
          ))}
        </div>
      </div>
      <div className="mt-1 flex justify-between text-xs text-stone-500">
        <span>{points[0] && dayLabel(points[0].date)}</span>
        <span>{points.length > 0 && dayLabel(points[points.length - 1].date)}</span>
      </div>
    </div>
  )
}

type BarRow = { key: string; label: string; value: number; note?: string }

/** Horizontal bars with the value at the tip. {@code max} defaults to the largest value. */
function HBars({ rows, max, empty }: { rows: BarRow[]; max?: number; empty?: string }) {
  if (rows.length === 0) return <p className="text-sm text-stone-500">{empty}</p>
  const scale = Math.max(1, max ?? Math.max(...rows.map((r) => r.value)))
  return (
    <ul className="space-y-2.5">
      {rows.map((r) => (
        <li key={r.key} className="grid grid-cols-[7.5rem_1fr] items-center gap-3 text-sm sm:grid-cols-[10rem_1fr]">
          <span className="truncate text-stone-700" title={r.label}>
            {r.label}
          </span>
          <span className="flex min-w-0 items-center gap-2">
            <span
              className="h-3 shrink-0 rounded-r bg-brand-500"
              style={{ width: r.value === 0 ? 0 : `max(3px, calc((100% - 7rem) * ${r.value / scale}))` }}
            />
            <span className="shrink-0 whitespace-nowrap text-stone-900 tabular-nums">
              {count(r.value)}
              {r.note && <span className="ml-1.5 text-xs text-stone-500">{r.note}</span>}
            </span>
          </span>
        </li>
      ))}
    </ul>
  )
}

function SourceTable({ rows, labelHeader, empty }: { rows: SourceRow[]; labelHeader: string; empty: string }) {
  return (
    <>
      <Table
        head={[labelHeader, 'New accounts', 'Bookings', 'Gross']}
        empty={empty}
        rows={rows.map((r) => [
          SOURCE_LABELS[r.source] ?? r.source,
          count(r.accounts),
          count(r.confirmed_bookings),
          rupees(r.gross_paise),
        ])}
      />
      {rows.length > 0 && (
        <p className="mt-2 text-xs text-stone-500">
          Accounts count where the person first found us; bookings count the visit that led to each booking.
        </p>
      )}
    </>
  )
}

function Table({ head, rows, empty }: { head: string[]; rows: ReactNode[][]; empty: string }) {
  if (rows.length === 0) return <p className="text-sm text-stone-500">{empty}</p>
  return (
    <div className="-mx-5 overflow-x-auto px-5 sm:-mx-6 sm:px-6">
      <table className="w-full min-w-md text-sm">
        <thead>
          <tr className="border-b border-stone-200 text-left text-xs text-stone-500">
            {head.map((h, i) => (
              <th key={h} className={`py-2 font-medium ${i > 0 ? 'pl-3 text-right' : ''}`}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-stone-100">
          {rows.map((cells, r) => (
            <tr key={r}>
              {cells.map((cell, i) => (
                <td key={i} className={`py-2 ${i > 0 ? 'pl-3 text-right tabular-nums text-stone-700' : 'text-stone-900'}`}>
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function FillMeter({ taken, size }: { taken: number; size: number }) {
  return (
    <span className="inline-flex items-center justify-end gap-2">
      <span className="h-2 w-16 overflow-hidden rounded-full bg-brand-100" aria-hidden="true">
        <span className="block h-full rounded-full bg-brand-500" style={{ width: `${(taken / size) * 100}%` }} />
      </span>
      <span className="tabular-nums">
        {taken}/{size}
      </span>
    </span>
  )
}
