import { getGuideQuestion, listGuideQuestions, replyAsGuide } from '../../api/questions.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { QuestionInbox } from '../../components/questions/QuestionInbox.tsx'

/**
 * /guide/questions and /guide/questions/:id (docs/TRD.md §7.21.6): questions trekkers sent through Empty Valley,
 * unanswered first, and a reply box. Rendered inside the guide layout.
 */
export function GuideQuestionsPage() {
  const { withAuth } = useAuth()
  return (
    <div className="mx-auto max-w-5xl space-y-5 px-5 py-6 sm:px-10">
      <h1 className="font-display text-2xl font-semibold">Questions</h1>
      <QuestionInbox
        perspective="guide"
        base="/guide/questions"
        queryKey={['guide-questions']}
        list={() => withAuth(listGuideQuestions)}
        get={(id) => withAuth((t) => getGuideQuestion(t, id))}
        reply={(id, body) => withAuth((t) => replyAsGuide(t, id, body))}
        empty={
          <p className="rounded-(--card-radius) border border-paper-300 bg-white p-8 text-center text-sm text-stone-600">
            No questions yet. When someone asks through your profile, you get an email and it shows up here. Answering
            quickly shows a short reply time on your page.
          </p>
        }
      />
    </div>
  )
}
