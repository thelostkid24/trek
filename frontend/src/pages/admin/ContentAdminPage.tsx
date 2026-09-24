import { ContentEditor } from '../../components/admin/ContentEditor.tsx'

/** /admin/content — the lists every trek page shows (docs/TRD.md §7.11). Each trek adds its own under Tracks. */
export function ContentAdminPage() {
  return (
    <>
      <p className="text-sm text-stone-600">
        These show on every trek page. A trek's own items (Tracks → Edit → Page lists) follow them.
      </p>
      <ContentEditor trackId={null} />
    </>
  )
}
