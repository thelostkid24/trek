import { Panel } from '../../components/admin/AdminUi.tsx'

/** /guide/reviews (docs/TRD.md §7.21.5). Not built yet. Rendered inside the guide layout. */
export function GuideReviewsPage() {
  return (
    <div className="mx-auto max-w-3xl px-5 py-6 sm:px-10">
      <Panel title="Reviews">
        <p className="text-sm text-stone-600">Coming soon. Ask trekkers for reviews and reply to them here.</p>
      </Panel>
    </div>
  )
}
