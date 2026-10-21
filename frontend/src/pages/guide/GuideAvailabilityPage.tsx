import { Panel } from '../../components/admin/AdminUi.tsx'

/** /guide/availability (docs/TRD.md §7.21.7). Not built yet. Rendered inside the guide layout. */
export function GuideAvailabilityPage() {
  return (
    <div className="mx-auto max-w-3xl px-5 py-6 sm:px-10">
      <Panel title="Availability">
        <p className="text-sm text-stone-600">Coming soon. Mark the dates you're free to lead, so the team can plan departures with you.</p>
      </Panel>
    </div>
  )
}
