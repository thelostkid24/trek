import { Link } from 'react-router-dom'
import { getMyQuestion, listMyQuestions, replyToMyQuestion } from '../../api/questions.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { QuestionInbox } from '../../components/questions/QuestionInbox.tsx'

/** /account/questions and /account/questions/:id (docs/TRD.md §7.21.6) — rendered inside <RequireAuth role="TREKKER">. */
export function QuestionsPage() {
  const { withAuth } = useAuth()
  return (
    <div className="max-w-6xl px-5 py-8 sm:px-10 sm:py-10">
      <h1 className="sr-only">Questions</h1>
      <QuestionInbox
        perspective="trekker"
        base="/account/questions"
        queryKey={['my-questions']}
        list={() => withAuth(listMyQuestions)}
        get={(id) => withAuth((t) => getMyQuestion(t, id))}
        reply={(id, body) => withAuth((t) => replyToMyQuestion(t, id, body))}
        empty={
          <div className="rounded-(--card-radius) border border-paper-300 bg-paper-50 p-8 text-center">
            <p className="font-display text-xl text-stone-900">No questions yet</p>
            <p className="mt-1 text-sm text-stone-600">Ask a guide from their profile; their replies land here and in your email.</p>
            <Link to="/guides" className="mt-4 inline-block rounded-full bg-pine-600 px-5 py-2.5 text-sm font-medium text-white hover:bg-pine-700">
              Meet the guides
            </Link>
          </div>
        }
      />
    </div>
  )
}
