import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { listGuides, promoteGuide } from '../../api/admin.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { ErrorNote, Loading, Panel, Button } from '../../components/admin/AdminUi.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { Avatar } from '../../components/Avatar.tsx'

export function GuidesAdminPage() {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const guides = useQuery({ queryKey: ['admin-guides'], queryFn: () => withAuth(listGuides) })
  const [email, setEmail] = useState('')
  const promote = useMutation({
    mutationFn: (value: string) => withAuth((token) => promoteGuide(token, value)),
    onSuccess: () => {
      setEmail('')
      void queryClient.invalidateQueries({ queryKey: ['admin-guides'] })
    },
  })

  const submit = (e: FormEvent) => {
    e.preventDefault()
    promote.mutate(email.trim())
  }

  return (
    <>
      <Panel title="Add a guide">
        <form onSubmit={submit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <TextField
              label="Email of an existing account"
              name="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={fieldErrors(promote.error).email}
              hint="They sign up as a trekker first. The guide role shows after their next sign-in."
            />
          </div>
          <Button type="submit" disabled={promote.isPending} className="sm:mb-6">
            {promote.isPending ? 'Adding…' : 'Make guide'}
          </Button>
        </form>
        {promote.error && !fieldErrors(promote.error).email && (
          <div className="mt-3">
            <ErrorNote error={promote.error} />
          </div>
        )}
      </Panel>

      <Panel title="Guides">
        {guides.isPending ? (
          <Loading />
        ) : guides.isError ? (
          <ErrorNote error={guides.error} />
        ) : guides.data.items.length === 0 ? (
          <p className="text-sm text-stone-600">No guides yet.</p>
        ) : (
          <ul className="divide-y divide-stone-100">
            {guides.data.items.map((g) => (
              <li key={g.id} className="flex items-center gap-3 py-3">
                <Avatar url={g.avatar_url} name={g.full_name} />
                <div className="min-w-0">
                  <p className="truncate font-medium">{g.full_name ?? 'No name yet'}</p>
                  <p className="truncate text-sm text-stone-500">{[g.email, g.phone].filter(Boolean).join(' · ')}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </>
  )
}
