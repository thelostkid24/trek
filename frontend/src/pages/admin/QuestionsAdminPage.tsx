import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { closeQuestion, getAdminQuestion, listAdminQuestions, type QuestionThread } from '../../api/questions.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Button, ErrorNote, Panel } from '../../components/admin/AdminUi.tsx'
import { QuestionInbox } from '../../components/questions/QuestionInbox.tsx'

const KEY = ['admin-questions']

/** /admin/questions and /admin/questions/:id (docs/TRD.md §7.21.6): every thread, read-only, and close (audit-logged). */
export function QuestionsAdminPage() {
  const { withAuth } = useAuth()
  return (
    <Panel title="Questions to guides">
      <QuestionInbox
        perspective="admin"
        base="/admin/questions"
        queryKey={KEY}
        list={() => withAuth((t) => listAdminQuestions(t))}
        get={(id) => withAuth((t) => getAdminQuestion(t, id))}
        empty={<p className="text-sm text-stone-600">No questions yet.</p>}
        threadActions={(thread) => (thread.can_reply ? <CloseThread thread={thread} /> : null)}
      />
    </Panel>
  )
}

function CloseThread({ thread }: { thread: QuestionThread }) {
  const { withAuth } = useAuth()
  const client = useQueryClient()
  const [note, setNote] = useState('')
  const close = useMutation({
    mutationFn: () => withAuth((t) => closeQuestion(t, thread.id, note.trim() || null)),
    onSuccess: (updated) => {
      client.setQueryData([...KEY, thread.id], updated)
      void client.invalidateQueries({ queryKey: KEY, exact: true })
    },
  })
  return (
    <div className="mt-5 space-y-2 border-t border-stone-200 pt-4">
      <label htmlFor="close-note" className="block text-sm font-medium text-stone-800">
        Close this conversation
      </label>
      <textarea
        id="close-note"
        value={note}
        onChange={(e) => setNote(e.target.value)}
        maxLength={2000}
        rows={2}
        placeholder="Optional note both sides will see"
        className="w-full rounded-lg border border-stone-300 px-3 py-2 text-sm"
      />
      <Button tone="danger" disabled={close.isPending} onClick={() => close.mutate()}>
        {close.isPending ? 'Closing…' : 'Close'}
      </Button>
      <ErrorNote error={close.error} />
    </div>
  )
}
