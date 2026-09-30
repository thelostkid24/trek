import { SNOWFALL_LABEL, type SnowReport, type Snowfall } from '../../api/catalog.ts'
import { dayLabel } from '../../lib/format.ts'

/** What a trekker reads first: a line under the big answer. */
const SNOWFALL_LINE: Record<Snowfall, string> = {
  NONE: 'No fresh snow on the trail this week.',
  LIGHT: 'A light cover on the trail this week.',
  HEAVY: 'Deep snow on the trail this week.',
}

/**
 * "This week on the trail": the one thing trekkers ask — has snow fallen? — from the newest weekly report
 * (docs/TRD.md §7.13), answered in a few lines of text. Before a report with snowfall, the panel says it's coming.
 */
export function SnowReportPanel({ report, place, trekName }: { report: SnowReport | null; place: string; trekName: string }) {
  const snowfall = report?.snowfall ?? null
  return (
    <section aria-labelledby="snow-heading" className="rounded-2xl bg-brand-900 p-5 text-white sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold tracking-[0.16em] text-laterite-400 uppercase">
            Snow report from {report?.reported_from ?? place}
          </p>
          <h2 id="snow-heading" className="mt-1 font-serif text-2xl">
            Is there snow on {trekName}?
          </h2>
        </div>
        <span className="rounded-full bg-white/10 px-3 py-1 text-xs text-brand-50">
          Updated every Tuesday{report ? ` · last: ${dayLabel(report.reported_on)}` : ''}
        </span>
      </div>

      {report && snowfall ? (
        <div className="mt-4">
          <p className="font-serif text-2xl">
            {snowfall === 'NONE' ? 'No' : 'Yes'}
            <span className="text-laterite-400"> · {SNOWFALL_LABEL[snowfall]}</span>
          </p>
          <p className="mt-2 max-w-3xl text-sm leading-relaxed text-brand-50">{describe(report, snowfall)}</p>
        </div>
      ) : (
        <p className="mt-4 text-sm text-brand-100">
          Whether snow has fallen on {trekName} will show here after the first Tuesday report of the season.
        </p>
      )}
    </section>
  )
}

/** Two or three plain sentences: the snowfall, the snowline and night cold when reported, then the guide's note. */
function describe(report: SnowReport, snowfall: Snowfall) {
  const facts = [
    report.snowline_m !== null && `the snowline sits around ${report.snowline_m.toLocaleString('en-IN')} m`,
    report.night_temp_c !== null && `nights drop to ${report.night_temp_c} °C`,
  ].filter(Boolean)
  const detail = facts.length > 0 ? ` ${facts.join(' and ').replace(/^./, (c) => c.toUpperCase())}.` : ''
  const note = report.note?.trim() ? ` ${report.note.trim()}` : ''
  return `${SNOWFALL_LINE[snowfall]}${detail}${note}`
}
