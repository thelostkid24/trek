import { useQuery } from '@tanstack/react-query'
import { getHealth } from '../api/client.ts'

export function HomePage() {
  const health = useQuery({ queryKey: ['health'], queryFn: getHealth })

  return (
    <section className="space-y-6">
      <div className="rounded-2xl bg-brand-900 px-5 py-10 text-white sm:px-10">
        <h1 className="text-2xl font-semibold sm:text-4xl">Small batches. Guaranteed departures.</h1>
        <p className="mt-3 max-w-xl text-brand-100">
          Book a seat with a vetted local guide. If you've paid, you're going.
        </p>
      </div>

      <div className="rounded-xl border border-stone-200 bg-white p-4 text-sm">
        <span className="font-medium">Backend: </span>
        {health.isPending && <span className="text-stone-500">checking…</span>}
        {health.isError && <span className="text-red-600">{health.error.message}</span>}
        {health.data && (
          <span className="text-brand-700">
            {health.data.status}, database {health.data.database}
          </span>
        )}
      </div>
    </section>
  )
}
