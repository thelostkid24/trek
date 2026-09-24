import type { CrowdCount, SnowReport } from '../../api/catalog.ts'
import { dayLabel, feet, parseDate } from '../../lib/format.ts'

/**
 * "This week on the trail": the newest weekly report (docs/TRD.md §7.13) and the tent counts that let
 * trekkers pick a quiet week. Before the first report of the season the panel says so.
 */
export function SnowReportPanel({ report, crowd, place }: { report: SnowReport | null; crowd: CrowdCount[]; place: string }) {
  const tiles: [string, string][] = []
  if (report) {
    const [first, ...rest] = report.conditions
    if (report.snowline_m !== null) tiles.push(['Snowline altitude', feet(report.snowline_m)])
    if (first) tiles.push([first.label, first.value])
    if (report.night_temp_c !== null) tiles.push(['Night temp, base camp', `${report.night_temp_c} °C`])
    rest.forEach((c) => tiles.push([c.label, c.value]))
  }

  return (
    <section aria-labelledby="snow-heading" className="rounded-2xl bg-brand-900 p-5 text-white sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold tracking-[0.16em] text-laterite-400 uppercase">
            Snow report from {report?.reported_from ?? place}
          </p>
          <h2 id="snow-heading" className="mt-1 font-serif text-2xl">
            This week on the trail
          </h2>
        </div>
        <span className="rounded-full bg-white/10 px-3 py-1 text-xs text-brand-50">
          Updated every Tuesday{report ? ` · last: ${dayLabel(report.reported_on)}` : ''}
        </span>
      </div>

      {report ? (
        <>
          <div className="mt-5 grid gap-3 sm:grid-cols-[1fr_1.3fr]">
            {report.photo_url ? (
              <img
                src={report.photo_url}
                alt={`The trail on ${dayLabel(report.reported_on)}`}
                className="aspect-[4/3] w-full rounded-xl object-cover sm:aspect-auto sm:h-full"
              />
            ) : (
              <div className="flex aspect-[4/3] items-center justify-center rounded-xl bg-white/5 text-sm text-brand-100 sm:aspect-auto sm:h-full">
                No photo this week
              </div>
            )}
            {tiles.length > 0 && (
              <dl className="grid grid-cols-2 gap-3">
                {tiles.map(([label, value]) => (
                  <div key={label} className="rounded-xl bg-white/5 px-3.5 py-3 ring-1 ring-white/15">
                    <dt className="text-[0.65rem] font-medium tracking-[0.12em] text-brand-100 uppercase">{label}</dt>
                    <dd className="mt-1 font-semibold">{value}</dd>
                  </div>
                ))}
              </dl>
            )}
          </div>
          {report.note && <p className="mt-4 text-sm leading-relaxed text-brand-50">{report.note}</p>}
        </>
      ) : (
        <p className="mt-4 text-sm text-brand-100">
          The first report of the season will appear here: snowline, whether the lake has frozen, night temperature at
          base camp, the road, and a photo taken that morning.
        </p>
      )}

      <Crowd counts={crowd} />
    </section>
  )
}

/** Tents counted at camp, one bar per report, so a quiet week is easy to spot. */
function Crowd({ counts }: { counts: CrowdCount[] }) {
  if (counts.length === 0) return null
  const most = Math.max(...counts.map((c) => c.tents), 1)
  const latestPlace = counts[counts.length - 1].place
  return (
    <div className="mt-5 border-t border-white/10 pt-4">
      <p className="text-xs text-brand-100">
        Tents at {latestPlace}, by report
      </p>
      <ol className="mt-3 flex h-20 items-end gap-1.5" aria-label={`Tents at ${latestPlace}`}>
        {counts.map((c, i) => (
          <li key={i} className="flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1">
            <span className="text-[0.65rem] text-brand-50">{c.tents}</span>
            <span
              className="w-full rounded-t bg-laterite-400/80"
              style={{ height: `${Math.max(4, (c.tents / most) * 60)}%` }}
              title={`${c.tents} tents on ${dayLabel(c.reported_on)}`}
            />
            <span className="text-[0.6rem] whitespace-nowrap text-brand-100">
              {parseDate(c.reported_on).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
            </span>
          </li>
        ))}
      </ol>
    </div>
  )
}
