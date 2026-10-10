import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
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
  type BlogPhoto,
  type BlogPost,
  type BlogPostInput,
} from '../../api/blog.ts'
import { fieldErrors } from '../../auth/errorMessages.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { Button, ErrorNote, Loading, Panel } from '../../components/admin/AdminUi.tsx'
import { TextField } from '../../components/auth/TextField.tsx'
import { BlogBody } from '../../components/blog/BlogBody.tsx'
import { SelectField, TextAreaField } from '../../components/profile/fields.tsx'
import { shrinkPhoto } from '../../lib/photos.ts'

// /admin/blog — categories (one level of sub-categories), and posts written as drafts, then published to /blog.
// Contract: docs/TRD.md §7.19.

const MAX_PHOTOS = 30
const CATEGORIES_KEY = ['admin-blog-categories']
const POSTS_KEY = ['admin-blog-posts']

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

/** Category options for a post: top-level ones, each followed by its sub-categories as "Parent › Child". */
function categoryOptions(all: BlogCategory[]) {
  return all
    .filter((c) => c.parent_id === null)
    .flatMap((top) => [
      { value: top.id, label: top.name },
      ...all.filter((c) => c.parent_id === top.id).map((c) => ({ value: c.id, label: `${top.name} › ${c.name}` })),
    ])
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
  const categories = useQuery({ queryKey: CATEGORIES_KEY, queryFn: () => withAuth(adminBlogCategories) })
  const noCategories = categories.data?.items.length === 0
  return (
    <Panel title="Posts" action={<Button disabled={noCategories} onClick={() => onEdit('new')}>New post</Button>}>
      {noCategories && <p className="mb-3 text-sm text-stone-600">Add a category below before writing the first post.</p>}
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
  const [editing, setEditing] = useState<BlogCategory | 'new' | null>(null)
  const all = categories.data?.items ?? []
  const tops = all.filter((c) => c.parent_id === null)
  return (
    <Panel title="Categories" action={editing === null && <Button tone="secondary" onClick={() => setEditing('new')}>Add category</Button>}>
      <p className="text-sm text-stone-600">
        A category (e.g. Kedarkantha, Snow) can have sub-categories one level down (e.g. Kedarkantha › Winter). The
        blog shows a category once it has a published post.
      </p>
      {editing === 'new' && <CategoryForm category={null} all={all} onDone={() => setEditing(null)} />}
      {categories.isPending ? (
        <div className="mt-4"><Loading /></div>
      ) : categories.isError ? (
        <div className="mt-4"><ErrorNote error={categories.error} /></div>
      ) : (
        <ul className="mt-4 divide-y divide-stone-100">
          {tops.flatMap((top) => [top, ...all.filter((c) => c.parent_id === top.id)]).map((c) =>
            editing !== 'new' && editing?.id === c.id ? (
              <li key={c.id} className="py-2">
                <CategoryForm category={c} all={all} onDone={() => setEditing(null)} />
              </li>
            ) : (
              <CategoryRow key={c.id} category={c} onEdit={() => setEditing(c)} />
            ),
          )}
        </ul>
      )}
    </Panel>
  )
}

function CategoryRow({ category: c, onEdit }: { category: BlogCategory; onEdit: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: () => withAuth((token) => deleteBlogCategory(token, c.id)),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY }),
  })
  return (
    <li className={`py-3 ${c.parent_id ? 'pl-6' : ''}`}>
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate font-medium">{c.parent_id ? `↳ ${c.name}` : c.name}</p>
          <p className="truncate text-sm text-stone-500">
            /blog?category={c.slug} · {c.published_posts} published
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          <Button tone="secondary" onClick={onEdit}>Edit</Button>
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

function CategoryForm({ category, all, onDone }: { category: BlogCategory | null; all: BlogCategory[]; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const [name, setName] = useState(category?.name ?? '')
  const [slug, setSlug] = useState(category?.slug ?? '')
  const [slugTouched, setSlugTouched] = useState(category !== null)
  const [parentId, setParentId] = useState(category?.parent_id ?? '')
  const save = useMutation({
    mutationFn: () => {
      const body = { name: name.trim(), slug, parent_id: parentId || null }
      return withAuth((token) => (category ? updateBlogCategory(token, category.id, body) : createBlogCategory(token, body)))
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY })
      void queryClient.invalidateQueries({ queryKey: POSTS_KEY })
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  // Only top-level categories can hold sub-categories, and one with sub-categories must stay top-level.
  const hasChildren = category !== null && all.some((c) => c.parent_id === category.id)
  const parents = all.filter((c) => c.parent_id === null && c.id !== category?.id)
  const submit = (e: FormEvent) => {
    e.preventDefault()
    save.mutate()
  }
  return (
    <form onSubmit={submit} className="mt-4 grid gap-3 rounded-xl bg-paper-100 p-4 sm:grid-cols-3">
      <TextField label="Name" name="category-name" required maxLength={60} value={name} error={errors.name}
        onChange={(e) => {
          setName(e.target.value)
          if (!slugTouched) setSlug(slugify(e.target.value, 80))
        }} />
      <TextField label="Slug" name="category-slug" required maxLength={80} value={slug} error={errors.slug}
        hint="Shown in the link" onChange={(e) => {
          setSlugTouched(true)
          setSlug(e.target.value)
        }} />
      <SelectField label="Under" name="category-parent" placeholder="Nothing (top level)" value={parentId}
        disabled={hasChildren} hint={hasChildren ? 'Has sub-categories, so it stays top level.' : undefined}
        error={errors.parent_id} onChange={(e) => setParentId(e.target.value)}
        options={parents.map((c) => ({ value: c.id, label: c.name }))} />
      <div className="flex flex-wrap items-center gap-2 sm:col-span-3">
        <Button type="submit" disabled={save.isPending}>{save.isPending ? 'Saving…' : category ? 'Save category' : 'Add category'}</Button>
        <Button tone="secondary" onClick={onDone}>Cancel</Button>
      </div>
      {save.error && Object.keys(errors).length === 0 && <div className="sm:col-span-3"><ErrorNote error={save.error} /></div>}
    </form>
  )
}

// --- Post editor

const FORMAT_HELP = 'Blank line = new paragraph · ## Heading · - list item · **bold** · [link text](https://…) · photos: use “Insert in post” below.'

function PostEditor({ postId, onSaved, onDone }: { postId: string | null; onSaved: (id: string) => void; onDone: () => void }) {
  const { withAuth } = useAuth()
  const post = useQuery({
    queryKey: ['admin-blog-post', postId],
    queryFn: () => withAuth((token) => adminBlogPost(token, postId as string)),
    enabled: postId !== null,
  })
  if (postId !== null && post.isPending) return <Loading />
  if (postId !== null && post.isError) return <ErrorNote error={post.error} />
  return <PostForm post={post.data ?? null} onSaved={onSaved} onDone={onDone} />
}

function PostForm({ post, onSaved, onDone }: { post: BlogPost | null; onSaved: (id: string) => void; onDone: () => void }) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()
  const categories = useQuery({ queryKey: CATEGORIES_KEY, queryFn: () => withAuth(adminBlogCategories) })
  const saved: BlogPostInput = {
    title: post?.title ?? '',
    slug: post?.slug ?? '',
    category_id: post?.category.id ?? '',
    excerpt: post?.excerpt ?? '',
    body: post?.body ?? '',
  }
  const [draft, setDraft] = useState<BlogPostInput>(saved)
  const [slugTouched, setSlugTouched] = useState(post !== null)
  const [preview, setPreview] = useState(false)
  /** Where the cursor last was in the body, so "Insert in post" lands there; null = the end. */
  const cursor = useRef<number | null>(null)
  const dirty = JSON.stringify(draft) !== JSON.stringify(saved)
  const published = post?.published_at != null

  const refresh = (updated: BlogPost) => {
    queryClient.setQueryData(['admin-blog-post', updated.id], updated)
    void queryClient.invalidateQueries({ queryKey: POSTS_KEY })
    void queryClient.invalidateQueries({ queryKey: CATEGORIES_KEY })
  }
  const save = useMutation({
    mutationFn: () => withAuth((token) => (post ? updateBlogPost(token, post.id, draft) : createBlogPost(token, draft))),
    onSuccess: (updated) => {
      refresh(updated)
      // A new post becomes an edit, so photos can be added to it.
      if (!post) onSaved(updated.id)
    },
  })
  const publish = useMutation({
    mutationFn: async (on: boolean) => {
      if (dirty) await save.mutateAsync()
      return withAuth((token) => setBlogPostPublished(token, post!.id, on))
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
  const errors = fieldErrors(save.error)
  const edit = (patch: Partial<BlogPostInput>) => setDraft((d) => ({ ...d, ...patch }))

  /** Puts a photo on its own line at the cursor (or the end) of the body. */
  const insertPhoto = (photo: BlogPhoto) => {
    const line = `![${(photo.caption ?? '').replace(/[[\]]/g, '')}](${photo.url})`
    const at = Math.min(cursor.current ?? draft.body.length, draft.body.length)
    const before = draft.body.slice(0, at).replace(/\s*$/, '')
    const after = draft.body.slice(at).replace(/^\s*/, '')
    const body = [before, line, after].filter(Boolean).join('\n\n')
    edit({ body })
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

      <Panel
        title={post ? 'Edit post' : 'New post'}
        action={post && <PublishedPill published={published} />}
      >
        <form onSubmit={submit} className="grid gap-4">
          <TextField label="Title" name="post-title" required maxLength={150} value={draft.title} error={errors.title}
            onChange={(e) => edit({ title: e.target.value, ...(slugTouched ? {} : { slug: slugify(e.target.value, 100) }) })} />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Slug" name="post-slug" required maxLength={100} value={draft.slug} error={errors.slug}
              hint={`theemptyvalley.com/blog/${draft.slug || '…'}${published ? ' — changing it breaks shared links' : ''}`}
              onChange={(e) => {
                setSlugTouched(true)
                edit({ slug: e.target.value })
              }} />
            <SelectField label="Category" name="post-category" required value={draft.category_id} error={errors.category_id}
              onChange={(e) => edit({ category_id: e.target.value })} options={categoryOptions(categories.data?.items ?? [])} />
          </div>
          <TextAreaField label="Excerpt" name="post-excerpt" maxLength={300} rows={2} value={draft.excerpt} error={errors.excerpt}
            hint="One or two lines under the title on the blog and in search results. Optional."
            onChange={(e) => edit({ excerpt: e.target.value })} />
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
          <div className="flex flex-wrap items-center gap-2">
            <Button type="submit" disabled={save.isPending || (post !== null && !dirty)}>
              {save.isPending ? 'Saving…' : post ? (dirty ? 'Save changes' : 'Saved') : 'Save draft'}
            </Button>
            {post && (
              <Button tone={published ? 'secondary' : 'primary'} disabled={publish.isPending}
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
          {save.error && Object.keys(errors).length === 0 && <ErrorNote error={save.error} />}
          {publish.error && <ErrorNote error={publish.error} />}
          {remove.error && <ErrorNote error={remove.error} />}
        </form>
      </Panel>

      {post ? (
        <PostPhotos post={post} onInsert={insertPhoto} onChange={refresh} />
      ) : (
        <p className="text-sm text-stone-600">Save the draft to add photos.</p>
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
        Pick one as the cover (it heads the post and its card on the blog). “Insert in post” puts a photo into the body
        where your cursor is; its caption shows under it and is read out by screen readers.
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
          <Button tone={isCover ? 'primary' : 'secondary'} onClick={onCover}>{isCover ? 'Cover ✓' : 'Make cover'}</Button>
          <Button tone="secondary" onClick={onInsert}>Insert in post</Button>
          <button type="button" onClick={onDelete} className="ml-auto text-xs font-medium text-laterite-600 hover:text-laterite-500">
            Delete
          </button>
        </div>
      </div>
    </li>
  )
}
