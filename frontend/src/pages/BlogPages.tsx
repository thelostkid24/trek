import { useQuery } from '@tanstack/react-query'
import { useEffect, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  getBlogCategory,
  getBlogPost,
  listBlogCategories,
  listBlogPosts,
  type BlogCategoryNode,
  type BlogCategoryPage,
  type BlogCategoryRef,
  type BlogPost,
  type BlogPostSummary,
} from '../api/blog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { BlogBody } from '../components/blog/BlogBody.tsx'
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

/** /blog — the latest post up top, the topics with posts, then everything else newest first. */
export function BlogPage() {
  const categories = useQuery({ queryKey: ['blog-categories'], queryFn: listBlogCategories })
  const posts = useQuery({ queryKey: ['blog-posts'], queryFn: () => listBlogPosts() })
  const topics = (categories.data?.items ?? []).filter((c) => c.published_posts > 0)
  const [featured, ...rest] = posts.data?.items ?? []

  return (
    <>
      <Seo title="Blog" description={BLOG_DESCRIPTION} path="/blog" />
      <section className="border-b border-stone-200">
        <div className="mx-auto max-w-6xl px-4 py-10 sm:py-14">
          <p className="text-xs font-semibold tracking-[0.18em] text-laterite-600 uppercase">The Empty Valley Journal</p>
          <h1 className="mt-3 font-display text-4xl font-light tracking-[-0.02em] sm:text-5xl">From the trail</h1>
          <p className="mt-3 max-w-2xl text-stone-600">
            Trek guides, planning and gear, altitude and safety, snow updates, and stories from the batches we've walked
            with.
          </p>
        </div>
      </section>

      <div className="mx-auto max-w-6xl space-y-14 px-4 py-10 sm:py-14">
        {posts.isPending ? (
          <div className="grid gap-6 lg:grid-cols-5" aria-busy="true" aria-label="Loading articles">
            <div className="aspect-[16/10] animate-pulse rounded-2xl bg-stone-100 lg:col-span-3" />
            <div className="h-48 animate-pulse rounded-2xl bg-stone-100 lg:col-span-2" />
          </div>
        ) : posts.isError ? (
          <Note>
            <p className="text-stone-700">{messageFor(posts.error)}</p>
            <button type="button" onClick={() => void posts.refetch()}
              className="mt-3 rounded-full bg-brand-900 px-5 py-2 text-sm font-medium text-white hover:bg-brand-800">
              Try again
            </button>
          </Note>
        ) : !featured ? (
          <Note>
            <p className="font-display text-xl text-stone-900">Stories are on their way</p>
            <p className="mt-1 text-sm text-stone-600">We're writing up the trail. Check back soon.</p>
          </Note>
        ) : (
          <FeaturedPost post={featured} />
        )}

        {topics.length > 0 && (
          <section aria-labelledby="topics">
            <h2 id="topics" className="font-display text-2xl font-light tracking-[-0.01em]">Browse by topic</h2>
            <ul className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {topics.map((c) => (
                <TopicCard key={c.id} category={c} />
              ))}
            </ul>
          </section>
        )}

        {rest.length > 0 && (
          <section aria-labelledby="latest">
            <h2 id="latest" className="font-display text-2xl font-light tracking-[-0.01em]">Latest articles</h2>
            <PostGrid posts={rest} />
          </section>
        )}
      </div>
    </>
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
      <section className="border-b border-stone-200">
        <div className="mx-auto max-w-6xl px-4 py-8 sm:py-12">
          <Breadcrumb trail={parent ? [parent] : []} />
          <h1 className="mt-3 font-display text-3xl font-light tracking-[-0.02em] sm:text-5xl">{c.name}</h1>
          {c.description && <p className="mt-3 max-w-2xl text-stone-600">{c.description}</p>}
          <p className="mt-2 text-sm text-stone-500">{c.published_posts === 1 ? '1 article' : `${c.published_posts} articles`}</p>
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
        </div>
      </section>
      <div className="mx-auto max-w-6xl px-4 py-10 sm:py-12">
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

function FeaturedPost({ post: p }: { post: BlogPostSummary }) {
  return (
    <article className="group relative grid overflow-hidden rounded-2xl bg-white ring-1 ring-stone-200 lg:grid-cols-5">
      <div className="lg:col-span-3">
        <Cover url={p.cover_url} className="aspect-[16/10] h-full w-full" />
      </div>
      <div className="flex flex-col justify-center p-6 sm:p-8 lg:col-span-2">
        <p className="text-xs font-semibold tracking-[0.12em] text-laterite-600 uppercase">Latest · {p.category.name}</p>
        <h2 className="mt-3 font-serif text-2xl leading-snug text-stone-900 sm:text-3xl">
          <Link to={`/blog/${p.slug}`} viewTransition className="after:absolute after:inset-0">{p.title}</Link>
        </h2>
        {p.excerpt && <p className="mt-3 text-stone-600">{p.excerpt}</p>}
        {p.published_at && <p className="mt-5 text-xs text-stone-500">{instantDate(p.published_at)}</p>}
        <span className="mt-5 text-sm font-medium text-brand-800 group-hover:text-brand-900">Read the story →</span>
      </div>
    </article>
  )
}

function TopicCard({ category: c }: { category: BlogCategoryNode }) {
  const subs = c.subcategories.filter((s) => s.published_posts > 0)
  return (
    <li className="relative flex flex-col rounded-2xl bg-white p-5 ring-1 ring-stone-200 transition hover:-translate-y-0.5 hover:shadow-lg hover:shadow-stone-900/5">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="font-serif text-xl text-stone-900">
          <Link to={`/blog/${c.slug}`} className="after:absolute after:inset-0">{c.name}</Link>
        </h3>
        <span className="shrink-0 text-xs text-stone-500">{c.published_posts}</span>
      </div>
      {c.description && <p className="mt-1 text-sm text-stone-600">{c.description}</p>}
      {subs.length > 0 && (
        <p className="mt-3 text-sm text-stone-500">{subs.map((s) => s.name).join(' · ')}</p>
      )}
    </li>
  )
}

function PostGrid({ posts }: { posts: BlogPostSummary[] }) {
  return (
    <ul className="mt-5 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      {posts.map((p) => (
        <li key={p.id} className="relative flex flex-col overflow-hidden rounded-2xl bg-white ring-1 ring-stone-200 transition hover:-translate-y-1 hover:shadow-lg hover:shadow-stone-900/5">
          <Cover url={p.cover_url} className="aspect-[16/10] w-full" />
          <div className="flex flex-1 flex-col p-5">
            <p className="text-xs font-semibold tracking-[0.12em] text-laterite-600 uppercase">{p.category.name}</p>
            <h3 className="mt-2 font-serif text-xl leading-snug text-stone-900">
              {/* The card is clickable through this link's overlay. */}
              <Link to={`/blog/${p.slug}`} viewTransition className="after:absolute after:inset-0">{p.title}</Link>
            </h3>
            {p.excerpt && <p className="mt-2 line-clamp-3 text-sm text-stone-600">{p.excerpt}</p>}
            {p.published_at && <p className="mt-auto pt-4 text-xs text-stone-500">{instantDate(p.published_at)}</p>}
          </div>
        </li>
      ))}
    </ul>
  )
}

function Cover({ url, className }: { url: string | null; className: string }) {
  return url ? (
    <img src={url} alt="" loading="lazy" className={`object-cover ${className}`} />
  ) : (
    <div className={`bg-gradient-to-br from-brand-100 to-paper-100 ${className}`} aria-hidden="true" />
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

function Note({ children }: { children: ReactNode }) {
  return <div className="rounded-2xl bg-white p-8 text-center ring-1 ring-stone-200">{children}</div>
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
