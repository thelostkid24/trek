import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, type ReactNode } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import {
  getBlogCategory,
  getBlogPost,
  listBlogCategories,
  listBlogPosts,
  type BlogCategoryPage,
  type BlogCategoryRef,
  type BlogPost,
  type BlogPostSummary,
} from '../api/blog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { BlogBody } from '../components/blog/BlogBody.tsx'
import { Ridgeline } from '../components/Ridgeline.tsx'
import { Seo } from '../components/Seo.tsx'
import { plainOpening } from '../lib/blogBody.ts'
import { parseDate } from '../lib/format.ts'
import { SITE_ORIGIN } from '../lib/siteLinks.ts'

// The public blog (docs/TRD.md §7.19). Posts live at /blog/<post>, categories at /blog/<category> and
// /blog/<category>/<sub-category>; the two never share a slug, so /blog/:slug is a category when it names one.

const BLOG_DESCRIPTION =
  'Trail notes from the Himalaya: trek guides, planning, gear, safety, snow updates and stories from our small-batch treks in Uttarakhand.'

const instantDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Asia/Kolkata' })

const calendarDate = (iso: string) =>
  parseDate(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })

/** A category's page, or a sub-category's under its parent. */
const categoryHref = (c: BlogCategoryRef) => (c.parent ? `/blog/${c.parent.slug}/${c.slug}` : `/blog/${c.slug}`)

const notFound = (error: unknown) => error instanceof ApiError && error.status === 404
const noRetryOn404 = (count: number, error: unknown) => !notFound(error) && count < 2

/** /blog — every article, filtered by category and sub-category, laid out like /treks. */
export function BlogPage() {
  const [params, setParams] = useSearchParams()
  const categories = useQuery({ queryKey: ['blog-categories'], queryFn: listBlogCategories })
  const posts = useQuery({ queryKey: ['blog-posts'], queryFn: () => listBlogPosts() })
  const all = useMemo(() => posts.data?.items ?? [], [posts.data])

  const tops = categories.data?.items ?? []
  const picked = tops.find((c) => c.slug === params.get('category')) ?? null
  const subs = (picked?.subcategories ?? []).filter((s) => s.published_posts > 0)
  const sub = subs.find((s) => s.slug === params.get('topic')) ?? null

  /** Picking a category clears its topic; picking the same one again clears both. */
  const pickCategory = (slug: string | null) => {
    const next = new URLSearchParams()
    if (slug && slug !== picked?.slug) next.set('category', slug)
    setParams(next, { replace: true })
  }
  const pickTopic = (slug: string | null) => {
    const next = new URLSearchParams(params)
    if (slug) next.set('topic', slug)
    else next.delete('topic')
    setParams(next, { replace: true })
  }

  const matching = all.filter((p) =>
    sub ? p.category.slug === sub.slug : !picked || (p.category.parent ?? p.category).slug === picked.slug,
  )

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:py-10">
      <Seo title="Blog" description={BLOG_DESCRIPTION} path="/blog" />
      <header>
        <h1 className="font-display text-3xl font-light tracking-[-0.02em] sm:text-4xl">From the trail</h1>
        <p className="mt-2 max-w-2xl text-sm text-stone-600 sm:text-base">
          Trek guides, planning and gear, altitude and safety, snow updates, and stories from the batches we've walked
          with.
        </p>
      </header>

      {tops.length > 0 && (
        <div className="mt-6 flex flex-wrap items-center gap-2" role="group" aria-label="Categories">
          <FilterChip on={!picked} onClick={() => pickCategory(null)}>
            All
          </FilterChip>
          {tops.map((c) => (
            <FilterChip key={c.id} on={c.id === picked?.id} onClick={() => pickCategory(c.slug)}>
              {c.name}
              {c.published_posts > 0 && <span className="ml-1.5 text-xs opacity-60">{c.published_posts}</span>}
            </FilterChip>
          ))}
          {subs.length > 0 && (
            <FilterSelect label="Topic" value={sub?.slug ?? null} onChange={pickTopic}>
              {subs.map((s) => (
                <option key={s.id} value={s.slug}>
                  {s.name}
                </option>
              ))}
            </FilterSelect>
          )}
        </div>
      )}

      {picked?.description && !sub && <p className="mt-4 max-w-2xl text-sm text-stone-600">{picked.description}</p>}
      {sub?.description && <p className="mt-4 max-w-2xl text-sm text-stone-600">{sub.description}</p>}

      {posts.isPending ? (
        <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Loading articles">
          {[0, 1, 2].map((i) => (
            <li key={i} className="h-80 animate-pulse rounded-2xl bg-stone-100" />
          ))}
        </ul>
      ) : posts.isError ? (
        <Note>
          <p className="text-stone-700">{messageFor(posts.error)}</p>
          <button type="button" onClick={() => void posts.refetch()}
            className="mt-3 rounded-full bg-brand-900 px-5 py-2 text-sm font-medium text-white hover:bg-brand-800">
            Try again
          </button>
        </Note>
      ) : matching.length === 0 ? (
        <Note>
          <p className="font-display text-xl text-stone-900">
            {all.length === 0 ? 'Stories are on their way' : `Nothing in ${(sub ?? picked)?.name ?? 'here'} yet`}
          </p>
          <p className="mt-1 text-sm text-stone-600">
            {all.length === 0 ? "We're writing up the trail. Check back soon." : 'Try another category.'}
          </p>
        </Note>
      ) : (
        <PostGrid posts={matching} />
      )}
    </div>
  )
}

/** /blog/:slug — a category's page when the slug names one of the ten, otherwise a post. */
export function BlogSlugPage() {
  const { slug = '' } = useParams()
  const categories = useQuery({ queryKey: ['blog-categories'], queryFn: listBlogCategories })
  if (categories.isPending) return <PageSkeleton />
  const isCategory = categories.data?.items.some((c) => c.slug === slug) ?? false
  return isCategory ? <CategoryView slug={slug} /> : <PostView slug={slug} />
}

/** /blog/:category/:sub — a sub-category's page. */
export function BlogSubCategoryPage() {
  const { sub = '' } = useParams()
  return <CategoryView slug={sub} />
}

function CategoryView({ slug }: { slug: string }) {
  const page = useQuery({ queryKey: ['blog-category', slug], queryFn: () => getBlogCategory(slug), retry: noRetryOn404 })
  if (page.isPending) return <PageSkeleton />
  if (page.isError) return <Missing what="category" error={page.error} />
  return <Category page={page.data} />
}

function Category({ page }: { page: BlogCategoryPage }) {
  const { category: c, parent } = page
  const here: BlogCategoryRef = { id: c.id, name: c.name, slug: c.slug, parent }
  // A sub-category's page shows its siblings, so readers can move across the topic.
  const siblingsOf = useQuery({ queryKey: ['blog-categories'], queryFn: listBlogCategories, enabled: parent !== null })
  const top = parent ? siblingsOf.data?.items.find((t) => t.id === parent.id) : c
  const chips = (top?.subcategories ?? []).filter((s) => s.published_posts > 0)
  return (
    <>
      <Seo
        title={parent ? `${c.name} · ${parent.name}` : c.name}
        description={c.description ?? `${c.name}: articles from The Empty Valley's trek journal.`}
        path={categoryHref(here)}
        noindex={!page.indexable}
      />
      <div className="mx-auto max-w-6xl px-4 py-8 sm:py-10">
        <Breadcrumb trail={parent ? [parent] : []} />
        <h1 className="mt-3 font-display text-3xl font-light tracking-[-0.02em] sm:text-4xl">{c.name}</h1>
        {c.description && <p className="mt-2 max-w-2xl text-sm text-stone-600 sm:text-base">{c.description}</p>}
        <p className="mt-1 text-sm text-stone-500">{c.published_posts === 1 ? '1 article' : `${c.published_posts} articles`}</p>
        {top && chips.length > 0 && (
          <nav aria-label={`${top.name} topics`} className="mt-6 flex flex-wrap gap-2">
            <Chip to={`/blog/${top.slug}`} active={!parent}>All</Chip>
            {chips.map((s) => (
              <Chip key={s.id} to={`/blog/${top.slug}/${s.slug}`} active={s.id === c.id}>
                {s.name}
              </Chip>
            ))}
          </nav>
        )}
        <PostGrid posts={page.posts} />
      </div>
    </>
  )
}

function PostView({ slug }: { slug: string }) {
  const navigate = useNavigate()
  const post = useQuery({ queryKey: ['blog-post', slug], queryFn: () => getBlogPost(slug), retry: noRetryOn404 })
  // An old slug is answered with the post under its new one (the API redirects); move the address bar along.
  const moved = post.data && post.data.slug !== slug ? post.data.slug : null
  useEffect(() => {
    if (moved) navigate(`/blog/${moved}`, { replace: true })
  }, [moved, navigate])
  if (post.isPending) return <PageSkeleton />
  if (post.isError) return <Missing what="post" error={post.error} />
  return <Article post={post.data} />
}

function Article({ post: p }: { post: BlogPost }) {
  const description = p.excerpt ?? plainOpening(p.body)
  const url = `${SITE_ORIGIN}/blog/${p.slug}`
  const edited = p.published_at !== null && p.updated_at.slice(0, 10) !== p.published_at.slice(0, 10)
  const answered = p.faqs.filter((f) => f.question && f.answer)
  const article = {
    '@type': p.schema_type === 'HOW_TO' ? 'HowTo' : 'BlogPosting',
    [p.schema_type === 'HOW_TO' ? 'name' : 'headline']: p.title,
    description,
    ...(p.cover_url ? { image: p.cover_url } : {}),
    ...(p.published_at ? { datePublished: p.published_at } : {}),
    dateModified: p.updated_at,
    ...(p.author_name ? { author: { '@type': 'Person', name: p.author_name } } : {}),
    mainEntityOfPage: url,
  }
  const faqPage = answered.length > 0 && {
    '@type': 'FAQPage',
    mainEntity: answered.map((f) => ({ '@type': 'Question', name: f.question, acceptedAnswer: { '@type': 'Answer', text: f.answer } })),
  }
  return (
    <article>
      <Seo
        title={p.title}
        description={description || BLOG_DESCRIPTION}
        path={`/blog/${p.slug}`}
        image={p.cover_url ?? undefined}
        jsonLd={{ '@context': 'https://schema.org', '@graph': faqPage ? [article, faqPage] : [article] }}
      />
      <header className="mx-auto max-w-3xl px-4 pt-8 sm:pt-12">
        <Breadcrumb trail={p.category.parent ? [p.category.parent, p.category] : [p.category]} />
        <h1 className="mt-4 font-display text-3xl leading-tight font-light tracking-[-0.02em] text-stone-900 sm:text-5xl">{p.title}</h1>
        {p.excerpt && <p className="mt-4 text-lg leading-relaxed text-stone-600">{p.excerpt}</p>}
        <p className="mt-5 flex flex-wrap gap-x-3 gap-y-1 text-sm text-stone-500">
          {p.author_name && <span>By <span className="font-medium text-stone-700">{p.author_name}</span></span>}
          {p.published_at && <span>{instantDate(p.published_at)}</span>}
          {edited && <span>Last updated {instantDate(p.updated_at)}</span>}
        </p>
      </header>

      {p.cover_url && (
        <figure className="mx-auto mt-8 max-w-5xl px-4">
          <img src={p.cover_url} alt={p.cover_caption ?? ''} className="aspect-[16/9] w-full rounded-(--card-radius) object-cover" />
          {(p.cover_caption || p.cover_taken_on) && (
            <figcaption className="mt-2 text-center text-sm text-stone-500">
              {[p.cover_caption, p.cover_taken_on && calendarDate(p.cover_taken_on)].filter(Boolean).join(' · ')}
            </figcaption>
          )}
        </figure>
      )}

      <div className="mx-auto max-w-3xl space-y-12 px-4 py-10 sm:py-12">
        <BlogBody body={p.body} photos={p.photos} />

        {answered.length > 0 && (
          <section aria-labelledby="faqs">
            <h2 id="faqs" className="font-display text-2xl font-medium text-stone-900">Questions people ask</h2>
            <div className="mt-4 divide-y divide-stone-200 rounded-2xl bg-white ring-1 ring-stone-200">
              {answered.map((f) => (
                <details key={f.question} className="group px-5 py-4">
                  <summary className="flex cursor-pointer list-none items-start justify-between gap-4 font-medium text-stone-900">
                    {f.question}
                    <span aria-hidden="true" className="mt-0.5 text-laterite-600 transition group-open:rotate-45">+</span>
                  </summary>
                  <p className="mt-3 leading-relaxed whitespace-pre-line text-stone-700">{f.answer}</p>
                </details>
              ))}
            </div>
          </section>
        )}

        {p.related_treks.length > 0 && (
          <section aria-labelledby="treks" className="rounded-2xl bg-paper-100 p-5 sm:p-6">
            <h2 id="treks" className="text-xs font-semibold tracking-[0.12em] text-laterite-600 uppercase">Treks in this story</h2>
            <ul className="mt-3 flex flex-wrap gap-2">
              {p.related_treks.map((t) => (
                <li key={t.id}>
                  <Link to={`/treks/${t.slug}`} className="inline-block rounded-full bg-white px-4 py-1.5 text-sm font-medium text-brand-800 ring-1 ring-stone-200 hover:ring-brand-400">
                    {t.name} →
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        )}

        <div className="border-t border-stone-200 pt-6">
          <Link to={categoryHref(p.category)} className="text-sm font-medium text-brand-800 hover:text-brand-900">
            ← More in {p.category.name}
          </Link>
        </div>
      </div>
    </article>
  )
}

// --- Pieces

function PostGrid({ posts }: { posts: BlogPostSummary[] }) {
  return (
    <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      {posts.map((p) => (
        <PostCard key={p.id} post={p} />
      ))}
    </ul>
  )
}

/** An article as a card, built like the trek cards on /treks. */
function PostCard({ post: p }: { post: BlogPostSummary }) {
  const top = p.category.parent ?? p.category
  return (
    <li className="group relative flex flex-col overflow-hidden rounded-2xl bg-white ring-1 ring-stone-200 transition hover:-translate-y-1 hover:shadow-lg hover:shadow-stone-900/5">
      <div className="relative aspect-[4/3] overflow-hidden bg-brand-950">
        {p.cover_url ? (
          <img src={p.cover_url} alt="" loading="lazy" className="size-full object-cover transition-transform duration-700 group-hover:scale-105" />
        ) : (
          <Ridgeline className="absolute inset-0 size-full transition-transform duration-700 group-hover:scale-105" />
        )}
      </div>

      <div className="flex flex-1 flex-col p-4">
        <div className="flex items-center justify-between gap-2">
          <span className="truncate text-xs text-stone-500">{top.name}</span>
          {p.category.parent && (
            <span className="shrink-0 rounded-full bg-brand-100 px-2.5 py-0.5 text-xs font-medium text-brand-800">{p.category.name}</span>
          )}
        </div>
        <h2 className="mt-2 font-display text-xl font-semibold leading-snug text-brand-950">
          {/* The card is clickable through this link's overlay. */}
          <Link to={`/blog/${p.slug}`} viewTransition className="after:absolute after:inset-0">{p.title}</Link>
        </h2>
        {p.excerpt && <p className="mt-1 line-clamp-2 text-sm text-stone-600">{p.excerpt}</p>}
        {p.published_at && <p className="mt-auto pt-3 text-xs text-stone-500">{instantDate(p.published_at)}</p>}
      </div>
    </li>
  )
}

function Breadcrumb({ trail }: { trail: BlogCategoryRef[] }) {
  return (
    <nav aria-label="Breadcrumb" className="flex flex-wrap items-center gap-1.5 text-sm">
      <Link to="/blog" className="text-brand-700 hover:text-brand-900">Blog</Link>
      {trail.map((c) => (
        <span key={c.id} className="flex items-center gap-1.5">
          <span aria-hidden="true" className="text-stone-400">/</span>
          <Link to={categoryHref(c)} className="text-brand-700 hover:text-brand-900">{c.name}</Link>
        </span>
      ))}
    </nav>
  )
}

function Chip({ to, active, children }: { to: string; active: boolean; children: ReactNode }) {
  return (
    <Link
      to={to}
      aria-current={active ? 'page' : undefined}
      className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
        active ? 'bg-brand-900 text-white' : 'bg-white text-stone-700 ring-1 ring-stone-300 hover:ring-brand-400'
      }`}
    >
      {children}
    </Link>
  )
}

function FilterChip({ on, onClick, children }: { on: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`rounded-full px-3 py-1.5 text-sm transition ${
        on ? 'bg-brand-900 text-white' : 'bg-white text-stone-700 ring-1 ring-stone-300 hover:ring-brand-400'
      }`}
    >
      {children}
    </button>
  )
}

/** The sub-category dropdown, styled like the chips (as on /treks). Empty value = the whole category. */
function FilterSelect({ label, value, onChange, children }: { label: string; value: string | null; onChange: (value: string | null) => void; children: ReactNode }) {
  const on = value !== null
  return (
    <span className="relative inline-flex">
      <select
        aria-label={label}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value || null)}
        className={`cursor-pointer appearance-none rounded-full py-1.5 pr-8 pl-3 text-sm transition ${
          on ? 'bg-brand-900 text-white' : 'bg-white text-stone-700 ring-1 ring-stone-300 hover:ring-brand-400'
        }`}
      >
        <option value="">{label}</option>
        {children}
      </select>
      <svg viewBox="0 0 12 12" className={`pointer-events-none absolute top-1/2 right-3 size-3 -translate-y-1/2 ${on ? 'text-white' : 'text-stone-500'}`} fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
        <path d="M3 4.5 6 7.5 9 4.5" />
      </svg>
    </span>
  )
}

function Note({ children }: { children: ReactNode }) {
  return <div className="mt-8 rounded-2xl bg-white p-8 text-center ring-1 ring-stone-200">{children}</div>
}

function PageSkeleton() {
  return (
    <div className="mx-auto max-w-3xl space-y-4 px-4 py-10" aria-busy="true" aria-label="Loading">
      <Seo title="Blog" />
      <div className="h-10 w-2/3 animate-pulse rounded-lg bg-stone-200" />
      <div className="aspect-[16/9] animate-pulse rounded-2xl bg-stone-100" />
    </div>
  )
}

function Missing({ what, error }: { what: 'post' | 'category'; error: unknown }) {
  const missing = notFound(error)
  return (
    <section className="mx-auto max-w-md px-4 py-16 text-center">
      <Seo title={missing ? 'Not found' : 'Blog'} noindex />
      <h1 className="font-display text-2xl font-semibold">{missing ? `This ${what} isn't here` : 'Something went wrong'}</h1>
      <p className="mt-2 text-stone-600">{missing ? 'It may have moved, or not be published yet.' : messageFor(error)}</p>
      <Link to="/blog" className="mt-4 inline-block text-brand-700 underline">
        See every article
      </Link>
    </section>
  )
}
