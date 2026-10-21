import { useQuery } from '@tanstack/react-query'
import { getReplyTime, replyTimeLabel } from '../../../api/questions.ts'
import type { GuideSectionProps } from './types.ts'

/**
 * "~2 h / reply time" in the stats row (§7.21.6): the median first reply over the last 90 days, worked out on every
 * read. Nothing shows until the guide has five questions in that window.
 */
export function ReplyTimeStat({ guide }: GuideSectionProps) {
  const replyTime = useQuery({
    queryKey: ['guide-reply-time', guide.id],
    queryFn: () => getReplyTime(guide.id),
    staleTime: 5 * 60_000,
  })
  const minutes = replyTime.data?.median_minutes
  if (minutes == null) return null
  return (
    <dl>
      <dd className="font-mono text-lg font-semibold text-stone-900">{replyTimeLabel(minutes)}</dd>
      <dt className="mt-0.5 text-[0.65rem] font-medium tracking-[0.12em] text-stone-500 uppercase">Reply time</dt>
    </dl>
  )
}
