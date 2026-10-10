import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { listTracks } from '../../api/admin.ts'
import {
  adminBlogCategories,
  adminBlogPost,
  adminBlogPosts,
  createBlogCategory,
  createBlogPost,
  deleteBlogCategory,
  deleteBlogPhoto,
  deleteBlogPost,
  describeBlogPhoto,
  setBlogPostCover,
  setBlogPostPublished,
  updateBlogCategory,
  updateBlogPost,
  uploadBlogPhoto,
  type BlogCategory,
  type BlogFaq,
  type BlogPhoto,
  type BlogPost,
  type BlogPostInput,
  type BlogSchemaType,
} from '../../api/blog.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Button, ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { BlogBody } from '../../components/blog/BlogBody.tsx'
import { SelectField, TextAreaField } from '../../components/profile/fields.tsx'
import { shrinkPhoto } from '../../lib/photos.ts'

// /admin/blog — posts written as drafts and published to /blog, and the sub-categories under the ten fixed
// categories. Contract: docs/TRD.md §7.19.

const MAX_PHOTOS = 30
const CATEGORIES_KEY = ['admin-blog-categories']
const POSTS_KEY = ['admin-blog-posts']

/** The money rule: we never publish pay, margins, cost breakdowns, ledgers or fee splits. Trek price and trekker costs are fine. */
const MONEY_WORDS =
  /\b(salar(y|ies)|wages?|margins?|ledgers?|payouts?|commissions?|profits?|fee splits?|cost breakdowns?|(guide|porter|staff|cook) (pay|share|fees?|earnings))\b/gi

const SCHEMA_TYPES: { value: BlogSchemaType; label: string }[] = [
  { value: 'ARTICLE', label: 'Article' },
  { value: 'FAQ_PAGE', label: 'FAQ page' },
  { value: 'HOW_TO', label: 'How-to' },
]

/** "Kedarkantha winter 2026" → "kedarkantha-winter-2026". */
function slugify(text: string, max: number): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, max)
    .replace(/-+$/, '')
}

/** Sub-categories only (a post is filed under exactly one), labelled "Category › Sub-category" in menu order. */
function subCategoryOptions(all: BlogCategory[]) {
  return all
    .filter((c) => c.parent_id === null)
    .flatMap((top) =>
      all.filter((c) => c.parent_id === top.id).map((c) => ({ value: c.id, label: `${top.name} › ${c.name}` })),
    )
}

const shortDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'Asia/Kolkata' })

export function BlogAdminPage() {
  // null = list; 'new' = writing a new post; otherwise the id of the post being edited.
  const [editing, setEditing] = useState<string | 'new' | null>(null)
  if (editing !== null) {
    return <PostEditor key={editing} postId={editing === 'new' ? null : editing} onSaved={setEditing} onDone={() => setEditing(null)} />
  }
  return (
    <>
      <Posts onEdit={setEditing} />
      <Categories />
    </>
  )
}

// --- Posts list

function Posts({ onEdit }: { onEdit: (id: string | 'new') => void }) {
  const { withAuth } = useAuth()
  const posts = useQuery({ queryKey: POSTS_KEY, queryFn: () => withAuth(adminBlogPosts) })
  return (
    <Panel title="Posts" action={<Button onClick={() => onEdit('new')}>New post</Button>}>
      {posts.isPending ? (
        <Loading />
      ) : posts.isError ? (
        <ErrorNote error={posts.error} />
      ) : posts.data.items.length === 0 ? (
        <p className="text-sm text-stone-600">No posts yet.</p>
      ) : (
        <ul className="divide-y divide-stone-100">
          {posts.data.items.map((p) => (
            <li key={p.id} className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="flex items-center gap-2 font-medium">
                  <span className="truncate">{p.title}</span>
                  <PublishedPill published={p.published_at !== null} />
                </p>
                <p className="truncate text-sm text-stone-500">
                  {p.category.parent ? `${p.category.parent.name} › ` : ''}
                  {p.category.name} · {p.published_at ? `published ${shortDate(p.published_at)}` : `edited ${shortDate(p.updated_at)}`}
                </p>
              </div>
              <Button tone="secondary" onClick={() => onEdit(p.id)}>Edit</Button>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

function PublishedPill({ published }: { published: boolean }) {
  return (
    <span className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${published ? 'bg-brand-100 text-brand-800' : 'bg-stone-100 text-stone-600'}`}>
      {published ? 'Published' : 'Draft'}
    </span>
  )
}

// --- Categories

function Categories() {
  const { withAuth } = useAuth()
  const categories = useQuery({ queryKey: CATEGORIES_KEY, queryFn: () => withAuth(adminBlogCategories) })
  // The category being edited, or the parent a new sub-category is being added under.
  const [editing, setEditing] = useState<{ category: BlogCategory | null; parentId: string | null } | null>(null)
  const all = categories.data?.items ?? []
  const tops = all.filter((c) => c.parent_id === null)
  const form = (category: BlogCategory | null, parentId: string | null) => (
    <CategoryForm category={category} parentId={parentId} onDone={() => setEditing(null)} />
  )
  return (
    <Panel title="Categories">
      <p className="text-sm text-stone-600">
        The ten categories are fixed. Every post is filed under one sub-category. A category shows in the Blog menu
        once it has 3 published posts; with 1–2 its page works but stays out of Google.
      </p>
      {categories.isPending ? (
        <div className="mt-4"><Loading /></div>
      ) : categories.isError ? (
        <div className="mt-4"><ErrorNote error={categories.error} /></div>
      ) : (
        <ul className="mt-4 space-y-3">
          {tops.map((top) => {
            const subs = all.filter((c) => c.parent_id === top.id)
            const total = top.published_posts + subs.reduce((n, s) => n + s.published_posts, 0)
            return (
              <li key={top.id} className="rounded-xl ring-1 ring-stone-200">
                {editing?.category?.id === top.id ? (
                  <div className="p-3">{form(top, null)}</div>
                ) : (
                  <div className="flex items-center justify-between gap-3 p-3">
                    <div className="min-w-0">
                      <p className="font-medium">
                        {top.name}
                        <span className={`ml-2 text-xs font-normal ${total >= 3 ? 'text-pine-700' : 'text-stone-500'}`}>
                          {total} published{total >= 3 ? ' · in menu' : ''}
                        </span>
                      </p>
                      <p className="truncate text-sm text-stone-500">/blog/{top.slug}</p>
                    </div>
                    <div className="flex shrink-0 gap-2">
                      <Button tone="secondary" onClick={() => setEditing({ category: top, parentId: null })}>Edit</Button>
                      <Button tone="secondary" onClick={() => setEditing({ category: null, parentId: top.id })}>Add sub-category</Button>
                    </div>
                  </div>
                )}
                <ul className="divide-y divide-stone-100 border-t border-stone-100">
                  {subs.map((s) =>
                    editing?.category?.id === s.id ? (
                      <li key={s.id} className="p-3">{form(s, top.id)}</li>
                    ) : (
                      <CategoryRow key={s.id} category={s} parentSlug={top.slug} onEdit={() => setEditing({ category: s, parentId: top.id })} />
                    ),
                  )}
                  {editing && editing.category === null && editing.parentId === top.id && <li className="p-3">{form(null, top.id)}</li>}
                </ul>
              </li>
            )
          })}
        </ul>
      )}
    </Panel>
  )
}

function CategoryRow({ category: c, parentSlug, onEdit }: { category: BlogCategory; parentSlug: string; onEdit: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: () => withAuth((token) => deleteBlogCategory(token, c.id)),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY }),
  })
  return (
    <li className="px-3 py-2 pl-6">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0 text-sm">
          <p className="truncate">
            ↳ {c.name} <span className="text-xs text-stone-500">· {c.published_posts} published</span>
          </p>
          <p className="truncate text-xs text-stone-500">/blog/{parentSlug}/{c.slug}</p>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          <button type="button" onClick={onEdit} className="text-xs font-medium text-brand-800 hover:text-brand-900">Edit</button>
          <button type="button" disabled={remove.isPending}
            onClick={() => window.confirm(`Delete “${c.name}”?`) && remove.mutate()}
            className="text-xs font-medium text-laterite-600 hover:text-laterite-500 disabled:opacity-50">
            Delete
          </button>
        </div>
      </div>
      {remove.error && <div className="mt-2"><ErrorNote error={remove.error} /></div>}
    </li>
  )
}

function CategoryForm({ category, parentId, onDone }: { category: BlogCategory | null; parentId: string | null; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState(category?.name ?? '')
  const [slug, setSlug] = useState(category?.slug ?? '')
  const [slugTouched, setSlugTouched] = useState(category !== null)
  const [description, setDescription] = useState(category?.description ?? '')
  const save = useMutation({
    mutationFn: () => {
      const body = { name: name.trim(), slug, description, parent_id: parentId }
      return withAuth((token) => (category ? updateBlogCategory(token, category.id, body) : createBlogCategory(token, body)))
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY })
      void queryClient.invalidateQueries({ queryKey: POSTS_KEY })
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  const submit = (e: FormEvent) => {
    e.preventDefault()
    save.mutate()
  }
  return (
    <form onSubmit={submit} className="grid gap-3 rounded-xl bg-paper-100 p-4 sm:grid-cols-2">
      <TextField label="Name" name="category-name" required maxLength={60} value={name} error={errors.name}
        onChange={(e) => {
          setName(e.target.value)
          if (!slugTouched) setSlug(slugify(e.target.value, 80))
        }} />
      <TextField label="Slug" name="category-slug" required maxLength={80} value={slug} error={errors.slug}
        hint={category?.published_posts ? 'Changing it changes the page’s link' : 'Shown in the link'}
        onChange={(e) => {
          setSlugTouched(true)
          setSlug(e.target.value)
        }} />
      <div className="sm:col-span-2">
        <TextAreaField label="Description" name="category-description" maxLength={300} rows={2} value={description}
          error={errors.description} hint="One line under the name on its page." onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="flex flex-wrap items-center gap-2 sm:col-span-2">
        <Button type="submit" disabled={save.isPending}>{save.isPending ? 'Saving…' : category ? 'Save' : 'Add sub-category'}</Button>
        <Button tone="secondary" onClick={onDone}>Cancel</Button>
      </div>
      {save.error && Object.keys(errors).length === 0 && <div className="sm:col-span-2"><ErrorNote error={save.error} /></div>}
    </form>
  )
}

// --- Post editor

const FORMAT_HELP = 'Blank line = new paragraph · ## Heading · - list item · **bold** · [link text](https://…) · photos: “Insert in post” below. Never copy bus or train times; link the route page.'

function PostEditor({ postId, onSaved, onDone }: { postId: string | null; onSaved: (id: string) => void; onDone: () => void }) {
  const { withAuth } = useAuth()
  // The editor replaces the list; start it at the top rather than where the list was scrolled.
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [])
  const post = useQuery({
    queryKey: ['admin-blog-post', postId],
    queryFn: () => withAuth((token) => adminBlogPost(token, postId as string)),
    enabled: postId !== null,
  })
  if (postId !== null && post.isPending) return <Loading />
  if (postId !== null && post.isError) return <ErrorNote error={post.error} />
  return <PostForm post={post.data ?? null} onSaved={onSaved} onDone={onDone} />
}

function toInput(post: BlogPost | null): BlogPostInput {
  return {
    title: post?.title ?? '',
    slug: post?.slug ?? '',
    category_id: post?.category.id ?? '',
    excerpt: post?.excerpt ?? '',
    body: post?.body ?? '',
    cover_caption: post?.cover_caption ?? '',
    cover_taken_on: post?.cover_taken_on ?? null,
    author_name: post?.author_name ?? '',
    faqs: post?.faqs ?? [],
    schema_type: post?.schema_type ?? 'ARTICLE',
    research_notes: post?.research_notes ?? '',
    track_ids: post?.related_treks.map((t) => t.id) ?? [],
  }
}

function PostForm({ post, onSaved, onDone }: { post: BlogPost | null; onSaved: (id: string) => void; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: CATEGORIES_KEY, queryFn: () => withAuth(adminBlogCategories) })
  const tracks = useQuery({ queryKey: ['admin-tracks'], queryFn: () => withAuth(listTracks) })
  const saved = toInput(post)
  const [draft, setDraft] = useState<BlogPostInput>(saved)
  const [slugTouched, setSlugTouched] = useState(post !== null)
  const [preview, setPreview] = useState(false)
  const [confirmed, setConfirmed] = useState(false)
  /** Where the cursor last was in the body, so "Insert in post" lands there; null = the end. */
  const cursor = useRef<number | null>(null)
  const dirty = JSON.stringify(draft) !== JSON.stringify(saved)
  const published = post?.published_at != null
  const moneyHits = [...new Set([draft.title, draft.excerpt, draft.body, ...draft.faqs.flatMap((f) => [f.question, f.answer])]
    .join('\n').match(MONEY_WORDS)?.map((w) => w.toLowerCase()) ?? [])]

  const refresh = (updated: BlogPost) => {
    queryClient.setQueryData(['admin-blog-post', updated.id], updated)
    void queryClient.invalidateQueries({ queryKey: POSTS_KEY })
    void queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY })
  }
  const body = (): BlogPostInput => ({ ...draft, faqs: draft.faqs.filter((f) => f.question.trim() || f.answer.trim()) })
  const save = useMutation({
    mutationFn: () => withAuth((token) => (post ? updateBlogPost(token, post.id, body()) : createBlogPost(token, body()))),
    onSuccess: (updated) => {
      refresh(updated)
      // A new post becomes an edit, so photos can be added to it.
      if (!post) onSaved(updated.id)
    },
  })
  const publish = useMutation({
    mutationFn: async (on: boolean) => {
      if (dirty) await save.mutateAsync()
      return withAuth((token) => setBlogPostPublished(token, post!.id, on, confirmed))
    },
    onSuccess: refresh,
  })
  const remove = useMutation({
    mutationFn: () => withAuth((token) => deleteBlogPost(token, post!.id)),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: POSTS_KEY })
      void queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY })
      onDone()
    },
  })
  const errors = { ...fieldErrors(publish.error), ...fieldErrors(save.error) }
  const edit = (patch: Partial<BlogPostInput>) => setDraft((d) => ({ ...d, ...patch }))
  const editFaq = (i: number, patch: Partial<BlogFaq>) =>
    edit({ faqs: draft.faqs.map((f, j) => (j === i ? { ...f, ...patch } : f)) })

  /** Puts a photo on its own line at the cursor (or the end) of the body. */
  const insertPhoto = (photo: BlogPhoto) => {
    const line = `![${(photo.caption ?? '').replace(/[[\]]/g, '')}](${photo.url})`
    const at = Math.min(cursor.current ?? draft.body.length, draft.body.length)
    const before = draft.body.slice(0, at).replace(/\s*$/, '')
    const after = draft.body.slice(at).replace(/^\s*/, '')
    edit({ body: [before, line, after].filter(Boolean).join('\n\n') })
    // The next photo goes after this one.
    cursor.current = (before ? before.length + 2 : 0) + line.length
    setPreview(false)
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    save.mutate()
  }

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <button type="button" onClick={() => (!dirty || window.confirm('Leave without saving?')) && onDone()}
          className="text-sm text-brand-700 hover:text-brand-900">
          ← All posts
        </button>
        {published && post && (
          <a href={`/blog/${post.slug}`} target="_blank" rel="noreferrer" className="text-sm text-brand-700 hover:text-brand-900">
            View on the blog ↗
          </a>
        )}
      </div>

      <Panel title={post ? 'Edit post' : 'New post'} action={post && <PublishedPill published={published} />}>
        <form onSubmit={submit} className="grid gap-5">
          <TextField label="Title" name="post-title" required maxLength={150} value={draft.title} error={errors.title}
            onChange={(e) => edit({ title: e.target.value, ...(slugTouched ? {} : { slug: slugify(e.target.value, 100) }) })} />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Slug" name="post-slug" required maxLength={100} value={draft.slug} error={errors.slug}
              hint={`theemptyvalley.com/blog/${draft.slug || '…'}${published ? ' — the old link will redirect' : ''}`}
              onChange={(e) => {
                setSlugTouched(true)
                edit({ slug: e.target.value })
              }} />
            <SelectField label="Sub-category" name="post-category" required value={draft.category_id} error={errors.category_id}
              placeholder="Pick one…" onChange={(e) => edit({ category_id: e.target.value })}
              options={subCategoryOptions(categories.data?.items ?? [])} />
          </div>
          <TextAreaField label="Excerpt" name="post-excerpt" maxLength={160} rows={2} value={draft.excerpt} error={errors.excerpt}
            hint="Under the title and the Google description. Needed to publish."
            onChange={(e) => edit({ excerpt: e.target.value })} />
          <div className="grid gap-4 sm:grid-cols-[1fr_12rem]">
            <TextField label="Hero image caption" name="post-cover-caption" maxLength={200} value={draft.cover_caption}
              error={errors.cover_caption} hint="Pick the hero image under Photos. Caption and date are needed to publish."
              onChange={(e) => edit({ cover_caption: e.target.value })} />
            <TextField label="Taken on" name="post-cover-date" type="date" value={draft.cover_taken_on ?? ''}
              error={errors.cover_taken_on} onChange={(e) => edit({ cover_taken_on: e.target.value || null })} />
          </div>
          {errors.cover_photo_id && <p className="-mt-3 text-sm text-laterite-600">Hero image: {errors.cover_photo_id}.</p>}

          <div>
            <div className="mb-1 flex gap-1 rounded-full bg-stone-100 p-1 text-sm sm:w-fit" role="tablist" aria-label="Body view">
              {(['Write', 'Preview'] as const).map((label) => (
                <button key={label} type="button" role="tab" aria-selected={preview === (label === 'Preview')}
                  onClick={() => setPreview(label === 'Preview')}
                  className={`rounded-full px-4 py-1 font-medium ${preview === (label === 'Preview') ? 'bg-white text-stone-900 shadow-sm' : 'text-stone-600'}`}>
                  {label}
                </button>
              ))}
            </div>
            {preview ? (
              <div className="rounded-xl border border-stone-200 p-4 sm:p-6">
                {draft.body.trim() ? <BlogBody body={draft.body} photos={post?.photos ?? []} /> : <p className="text-sm text-stone-500">Nothing written yet.</p>}
              </div>
            ) : (
              <TextAreaField label="Body" name="post-body" required maxLength={50000} rows={18} value={draft.body}
                error={errors.body} hint={FORMAT_HELP} onChange={(e) => edit({ body: e.target.value })}
                onSelect={(e) => (cursor.current = e.currentTarget.selectionStart)} />
            )}
          </div>

          <fieldset className="grid gap-3">
            <legend className="text-sm font-medium text-stone-800">FAQs</legend>
            <p className="-mt-1 text-xs text-stone-500">Shown at the end of the post and marked up for Google.</p>
            {draft.faqs.map((f, i) => (
              <div key={i} className="grid gap-2 rounded-xl bg-paper-100 p-3">
                <TextField label={`Question ${i + 1}`} name={`faq-${i}-q`} maxLength={200} value={f.question}
                  error={errors[`faqs[${i}].question`]} onChange={(e) => editFaq(i, { question: e.target.value })} />
                <TextAreaField label="Answer" name={`faq-${i}-a`} maxLength={2000} rows={2} value={f.answer}
                  error={errors[`faqs[${i}].answer`]} onChange={(e) => editFaq(i, { answer: e.target.value })} />
                <button type="button" onClick={() => edit({ faqs: draft.faqs.filter((_, j) => j !== i) })}
                  className="justify-self-end text-xs font-medium text-laterite-600 hover:text-laterite-500">
                  Remove
                </button>
              </div>
            ))}
            {draft.faqs.length < 20 && (
              <Button tone="secondary" className="justify-self-start" onClick={() => edit({ faqs: [...draft.faqs, { question: '', answer: '' }] })}>
                Add a question
              </Button>
            )}
          </fieldset>

          <fieldset>
            <legend className="text-sm font-medium text-stone-800">Related treks</legend>
            <p className="text-xs text-stone-500">None for general posts like a gear guide.</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {(tracks.data?.items ?? []).map((t) => {
                const on = draft.track_ids.includes(t.id)
                return (
                  <label key={t.id} className={`cursor-pointer rounded-full px-3 py-1.5 text-sm ring-1 ${on ? 'bg-brand-900 text-white ring-brand-900' : 'bg-white text-stone-700 ring-stone-300'}`}>
                    <input type="checkbox" className="sr-only" checked={on}
                      onChange={() => edit({ track_ids: on ? draft.track_ids.filter((id) => id !== t.id) : [...draft.track_ids, t.id] })} />
                    {t.name}
                  </label>
                )
              })}
            </div>
            {errors.track_ids && <p className="mt-1 text-sm text-laterite-600">{errors.track_ids}</p>}
          </fieldset>

          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Author" name="post-author" maxLength={100} value={draft.author_name} error={errors.author_name}
              hint="The byline. Leave blank to use your name." onChange={(e) => edit({ author_name: e.target.value })} />
            <SelectField label="Schema type" name="post-schema" value={draft.schema_type === 'ARTICLE' ? '' : draft.schema_type} placeholder="Article"
              onChange={(e) => edit({ schema_type: (e.target.value || 'ARTICLE') as BlogSchemaType })}
              options={SCHEMA_TYPES.filter((t) => t.value !== 'ARTICLE')} hint="How Google reads the page." />
          </div>
          <TextAreaField label="Research notes" name="post-notes" maxLength={20000} rows={3} value={draft.research_notes}
            error={errors.research_notes} hint="Admin only. Never shown on the site."
            onChange={(e) => edit({ research_notes: e.target.value })} />

          {moneyHits.length > 0 && (
            <p role="alert" className="rounded-lg bg-amber-100 px-4 py-3 text-sm text-amber-900">
              <span className="font-semibold">Money rule:</span> this post mentions {moneyHits.map((w) => `“${w}”`).join(', ')}. We never
              publish pay, margins, cost breakdowns, ledgers or fee splits. The trek price and trekkers’ own costs are fine.
            </p>
          )}
          {post && !published && (
            <label className="flex cursor-pointer items-start gap-2 text-sm text-stone-700">
              <input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} className="mt-0.5 size-4 accent-brand-800" />
              <span>
                This post publishes none of our money: no guide, porter or staff pay, margins, cost breakdowns, ledgers or fee splits.
                {errors.money_rule_confirmed && <span className="block text-laterite-600">Tick this to publish.</span>}
              </span>
            </label>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <Button type="submit" disabled={save.isPending || (post !== null && !dirty)}>
              {save.isPending ? 'Saving…' : post ? (dirty ? 'Save changes' : 'Saved') : 'Save draft'}
            </Button>
            {post && (
              <Button tone={published ? 'secondary' : 'primary'} disabled={publish.isPending || (!published && !confirmed)}
                onClick={() => publish.mutate(!published)}>
                {publish.isPending ? 'Working…' : published ? 'Unpublish' : dirty ? 'Save and publish' : 'Publish'}
              </Button>
            )}
            {post && (
              <button type="button" disabled={remove.isPending}
                onClick={() => window.confirm('Delete this post and its photos? This can’t be undone.') && remove.mutate()}
                className="ml-auto text-sm font-medium text-laterite-600 hover:text-laterite-500 disabled:opacity-50">
                Delete post
              </button>
            )}
          </div>
          {save.error && Object.keys(fieldErrors(save.error)).length === 0 && <ErrorNote error={save.error} />}
          {publish.error && Object.keys(fieldErrors(publish.error)).length > 0 && (
            <ErrorNote error={new Error(`Not ready to publish: ${Object.values(fieldErrors(publish.error)).join('; ')}.`)} />
          )}
          {publish.error && Object.keys(fieldErrors(publish.error)).length === 0 && <ErrorNote error={publish.error} />}
          {remove.error && <ErrorNote error={remove.error} />}
        </form>
      </Panel>

      {post ? (
        <PostPhotos post={post} onInsert={insertPhoto} onChange={refresh} />
      ) : (
        <p className="text-sm text-stone-600">Save the draft to add photos and the hero image.</p>
      )}
    </>
  )
}

function PostPhotos({ post, onInsert, onChange }: { post: BlogPost; onInsert: (photo: BlogPhoto) => void; onChange: (post: BlogPost) => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [caption, setCaption] = useState('')
  const [progress, setProgress] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const reload = () => queryClient.invalidateQueries({ queryKey: ['admin-blog-post', post.id] })

  const upload = useMutation({
    mutationFn: async (files: File[]) => {
      for (const [i, file] of files.entries()) {
        setProgress(files.length > 1 ? `Uploading ${i + 1} of ${files.length}…` : 'Uploading…')
        const blob = await shrinkPhoto(file).catch(() => file)
        await withAuth((token) => uploadBlogPhoto(token, post.id, blob, caption))
      }
    },
    onSuccess: () => setCaption(''),
    onSettled: () => {
      setProgress(null)
      if (input.current) input.current.value = ''
      void reload()
    },
  })
  const cover = useMutation({
    mutationFn: (photoId: string | null) => withAuth((token) => setBlogPostCover(token, post.id, photoId)),
    onSuccess: onChange,
  })
  const remove = useMutation({
    mutationFn: (photoId: string) => withAuth((token) => deleteBlogPhoto(token, post.id, photoId)),
    onSettled: () => {
      void reload()
      void queryClient.invalidateQueries({ queryKey: POSTS_KEY })
    },
  })
  const full = post.photos.length >= MAX_PHOTOS
  const uploadErrors = fieldErrors(upload.error)

  return (
    <Panel title={`Photos (${post.photos.length} of ${MAX_PHOTOS})`}>
      <p className="text-sm text-stone-600">
        Pick one as the hero image (it heads the post and its card). “Insert in post” puts a photo into the body where
        your cursor is; its caption shows under it and is read out by screen readers.
      </p>
      {post.photos.length > 0 && (
        <ul className="mt-4 grid gap-3 sm:grid-cols-2">
          {post.photos.map((p) => (
            <PhotoCard key={p.id} post={post} photo={p} isCover={post.cover_photo_id === p.id}
              onCover={() => cover.mutate(post.cover_photo_id === p.id ? null : p.id)} onInsert={() => onInsert(p)}
              onDelete={() => window.confirm('Delete this photo? Any place it’s inserted in the body will show nothing.') && remove.mutate(p.id)} />
          ))}
        </ul>
      )}
      <div className="mt-5 grid gap-3 sm:grid-cols-[1fr_auto] sm:items-end">
        <TextField label="Caption" name="blog-photo-caption" maxLength={200} value={caption} error={uploadErrors.caption}
          hint="Say what’s in the photo. Applies to every photo in this upload; edit each one after."
          onChange={(e) => setCaption(e.target.value)} />
        <div>
          <input ref={input} type="file" accept="image/jpeg,image/png" multiple className="sr-only" id="blog-photo-input"
            disabled={full || upload.isPending}
            onChange={(e) => {
              const files = Array.from(e.target.files ?? []).slice(0, MAX_PHOTOS - post.photos.length)
              if (files.length > 0) upload.mutate(files)
            }} />
          <Button disabled={full || upload.isPending} onClick={() => input.current?.click()}>{progress ?? 'Add photos'}</Button>
        </div>
      </div>
      {full && <p className="mt-3 text-sm text-stone-600">That's the limit. Delete one to add another.</p>}
      {upload.error && Object.keys(uploadErrors).length === 0 && <div className="mt-3"><ErrorNote error={upload.error} /></div>}
      {cover.error && <div className="mt-3"><ErrorNote error={cover.error} /></div>}
      {remove.error && <div className="mt-3"><ErrorNote error={remove.error} /></div>}
    </Panel>
  )
}

function PhotoCard({ post, photo, isCover, onCover, onInsert, onDelete }: {
  post: BlogPost
  photo: BlogPhoto
  isCover: boolean
  onCover: () => void
  onInsert: () => void
  onDelete: () => void
}) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [caption, setCaption] = useState(photo.caption ?? '')
  const save = useMutation({
    mutationFn: () => withAuth((token) => describeBlogPhoto(token, post.id, photo.id, caption)),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['admin-blog-post', post.id] }),
  })
  const dirty = caption !== (photo.caption ?? '')
  return (
    <li className={`overflow-hidden rounded-xl ring-1 ${isCover ? 'ring-2 ring-brand-700' : 'ring-stone-200'}`}>
      <img src={photo.url} alt={photo.caption ?? ''} loading="lazy" className="aspect-[16/9] w-full object-cover" />
      <div className="grid gap-2 p-3">
        <TextField label="Caption" name={`blog-caption-${photo.id}`} maxLength={200} value={caption}
          error={fieldErrors(save.error).caption} onChange={(e) => setCaption(e.target.value)} />
        <div className="flex flex-wrap items-center gap-2">
          {dirty && (
            <Button tone="secondary" disabled={save.isPending} onClick={() => save.mutate()}>
              {save.isPending ? 'Saving…' : 'Save caption'}
            </Button>
          )}
          <Button tone={isCover ? 'primary' : 'secondary'} onClick={onCover}>{isCover ? 'Hero ✓' : 'Make hero'}</Button>
          <Button tone="secondary" onClick={onInsert}>Insert in post</Button>
          <button type="button" onClick={onDelete} className="ml-auto text-xs font-medium text-laterite-600 hover:text-laterite-500">
            Delete
          </button>
        </div>
      </div>
    </li>
  )
}
