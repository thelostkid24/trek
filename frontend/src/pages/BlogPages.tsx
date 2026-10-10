import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import {
  getBlogPost,
  listBlogCategories,
  listBlogPosts,
  type BlogCategory,
  type BlogCategoryRef,
  type BlogPost,
  type BlogPostSummary,
} from '../api/blog.ts'
import { ApiError } from '../api/client.ts'
import { messageFor } from '../auth/errorMessages.ts'
import { BlogBody } from '../components/blog/BlogBody.tsx'
import { plainOpening } from '../lib/blogBody.ts'
import { Seo } from '../components/Seo.tsx'
import { SITE_ORIGIN } from '../lib/siteLinks.ts'

const BLOG_DESCRIPTION =
  'Trail notes from the Himalaya: trek guides, snow and season updates, and stories from our small-batch treks in Uttarakhand.'

const postDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'Asia/Kolkata' })

/** "Treks · Kedarkantha" for a sub-category, "Snow" for a top-level one. */
const categoryPath = (c: BlogCategoryRef) => (c.parent ? `${c.parent.name} · ${c.name}` : c.name)

/** /blog, /blog?category=<slug> — published posts, newest first, filtered by category. Contract: docs/TRD.md §7.19. */
export function BlogPage() {
  const [params, setParams] = useSearchParams()
  const selected = params.get('category') ?? ''
  const categories = useQuery({ queryKey: ['blog-categories'], queryFn: listBlogCategories })
  const posts = useQuery({
    queryKey: ['blog-posts', selected],
    queryFn: () => listBlogPosts(selected || undefined),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })

  const all = categories.data?.items ?? []
  const choose = (slug: string) => setParams(slug ? { category: slug } : {}, { replace: true })
  const current = all.find((c) => c.slug === selected)
  // The chips: top-level categories with posts (their own or a sub-category's), then the open one's sub-categories.
  const tops = all.filter((c) => c.parent_id === null && postsUnder(c, all) > 0)
  const openTop = current ? (current.parent_id ? all.find((c) => c.id === current.parent_id) : current) : undefined
  const subs = openTop ? all.filter((c) => c.parent_id === openTop.id && c.published_posts > 0) : []

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:py-10">
      <Seo title={current ? `${current.name} · Blog` : 'Blog'} description={BLOG_DESCRIPTION} path="/blog" />
      <header>
        <h1 className="font-display text-3xl font-light tracking-[-0.02em] sm:text-4xl">From the trail</h1>
        <p className="mt-2 max-w-2xl text-stone-600">
          Trek guides, snow and season updates, and stories from the batches we've walked with.
        </p>
      </header>

      {tops.length > 0 && (
        <nav aria-label="Blog categories" className="mt-6 space-y-2">
          <div className="flex flex-wrap gap-2">
            <Chip active={!selected} onClick={() => choose('')}>All</Chip>
            {tops.map((c) => (
              <Chip key={c.id} active={openTop?.id === c.id} onClick={() => choose(c.slug)}>{c.name}</Chip>
            ))}
          </div>
          {subs.length > 0 && openTop && (
            <div className="flex flex-wrap gap-2 border-l-2 border-laterite-400/50 pl-3">
              <Chip small active={current?.id === openTop.id} onClick={() => choose(openTop.slug)}>All {openTop.name}</Chip>
              {subs.map((c) => (
                <Chip small key={c.id} active={current?.id === c.id} onClick={() => choose(c.slug)}>{c.name}</Chip>
              ))}
            </div>
          )}
        </nav>
      )}

      {posts.isPending ? (
        <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Loading posts">
          {[0, 1, 2].map((i) => (
            <li key={i} className="h-72 animate-pulse rounded-2xl bg-stone-100" />
          ))}
        </ul>
      ) : posts.isError ? (
        <Note>
          {posts.error instanceof ApiError && posts.error.status === 404 ? (
            <>
              <p className="text-stone-700">That category isn't here any more.</p>
              <button type="button" onClick={() => choose('')} className="mt-3 text-sm font-medium text-brand-800 underline">
                See every post
              </button>
            </>
          ) : (
            <>
              <p className="text-stone-700">{messageFor(posts.error)}</p>
              <button type="button" onClick={() => void posts.refetch()}
                className="mt-3 rounded-full bg-brand-900 px-5 py-2 text-sm font-medium text-white hover:bg-brand-800">
                Try again
              </button>
            </>
          )}
        </Note>
      ) : posts.data.items.length === 0 ? (
        <Note>
          <p className="font-display text-xl text-stone-900">Stories are on their way</p>
          <p className="mt-1 text-sm text-stone-600">We're writing up the trail. Check back soon.</p>
        </Note>
      ) : (
        <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {posts.data.items.map((p) => (
            <PostCard key={p.id} post={p} />
          ))}
        </ul>
      )}
    </div>
  )
}

/** Published posts in a category and its sub-categories. */
function postsUnder(c: BlogCategory, all: BlogCategory[]): number {
  return c.published_posts + all.filter((s) => s.parent_id === c.id).reduce((n, s) => n + s.published_posts, 0)
}

function Chip({ active, small = false, onClick, children }: { active: boolean; small?: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={`rounded-full font-medium transition ${small ? 'px-3 py-1 text-xs' : 'px-4 py-1.5 text-sm'} ${
        active ? 'bg-brand-900 text-white' : 'bg-white text-stone-700 ring-1 ring-stone-300 hover:ring-brand-400'
      }`}
    >
      {children}
    </button>
  )
}

function PostCard({ post: p }: { post: BlogPostSummary }) {
  return (
    <li className="relative flex flex-col overflow-hidden rounded-2xl bg-white ring-1 ring-stone-200 transition hover:-translate-y-1 hover:shadow-lg hover:shadow-stone-900/5">
      {p.cover_url ? (
        <img src={p.cover_url} alt="" loading="lazy" className="aspect-[16/10] w-full object-cover" />
      ) : (
        <div className="aspect-[16/10] w-full bg-gradient-to-br from-brand-100 to-paper-100" aria-hidden="true" />
      )}
      <div className="flex flex-1 flex-col p-5">
        <p className="text-xs font-semibold tracking-[0.12em] text-laterite-600 uppercase">{categoryPath(p.category)}</p>
        <h2 className="mt-2 font-serif text-xl leading-snug text-stone-900">
          {/* The card is clickable through this link's overlay. */}
          <Link to={`/blog/${p.slug}`} viewTransition className="after:absolute after:inset-0">
            {p.title}
          </Link>
        </h2>
        {p.excerpt && <p className="mt-2 line-clamp-3 text-sm text-stone-600">{p.excerpt}</p>}
        {p.published_at && <p className="mt-auto pt-4 text-xs text-stone-500">{postDate(p.published_at)}</p>}
      </div>
    </li>
  )
}

function Note({ children }: { children: ReactNode }) {
  return <div className="mt-8 rounded-2xl bg-white p-8 text-center ring-1 ring-stone-200">{children}</div>
}

/** /blog/:slug — one published post. */
export function BlogPostPage() {
  const { slug = '' } = useParams()
  const post = useQuery({
    queryKey: ['blog-post', slug],
    queryFn: () => getBlogPost(slug),
    retry: (count, error) => !(error instanceof ApiError && error.status === 404) && count < 2,
  })

  if (post.isPending) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 px-4 py-10" aria-busy="true" aria-label="Loading post">
        <Seo title="Blog" />
        <div className="h-10 w-2/3 animate-pulse rounded-lg bg-stone-200" />
        <div className="aspect-[16/9] animate-pulse rounded-2xl bg-stone-100" />
      </div>
    )
  }
  if (post.isError) {
    const missing = post.error instanceof ApiError && post.error.status === 404
    return (
      <section className="mx-auto max-w-md px-4 py-16 text-center">
        <Seo title="Post not found" noindex />
        <h1 className="font-display text-2xl font-semibold">{missing ? 'Post not found' : 'Something went wrong'}</h1>
        <p className="mt-2 text-stone-600">{missing ? 'It may have been taken down or moved.' : messageFor(post.error)}</p>
        <Link to="/blog" className="mt-4 inline-block text-brand-700 underline">
          See every post
        </Link>
      </section>
    )
  }
  return <Article post={post.data} />
}

function Article({ post: p }: { post: BlogPost }) {
  const description = p.excerpt ?? plainOpening(p.body)
  const crumbs = p.category.parent ? [p.category.parent, p.category] : [p.category]
  return (
    <article className="mx-auto max-w-3xl px-4 py-8 sm:py-12">
      <Seo
        title={p.title}
        description={description || BLOG_DESCRIPTION}
        path={`/blog/${p.slug}`}
        image={p.cover_url ?? undefined}
        jsonLd={{
          '@context': 'https://schema.org',
          '@type': 'BlogPosting',
          headline: p.title,
          description,
          ...(p.cover_url ? { image: p.cover_url } : {}),
          ...(p.published_at ? { datePublished: p.published_at } : {}),
          dateModified: p.updated_at,
          ...(p.author_name ? { author: { '@type': 'Person', name: p.author_name } } : {}),
          mainEntityOfPage: `${SITE_ORIGIN}/blog/${p.slug}`,
        }}
      />
      <nav aria-label="Breadcrumb" className="flex flex-wrap items-center gap-1.5 text-sm">
        <Link to="/blog" className="text-brand-700 hover:text-brand-900">Blog</Link>
        {crumbs.map((c) => (
          <span key={c.id} className="flex items-center gap-1.5">
            <span aria-hidden="true" className="text-stone-400">/</span>
            <Link to={`/blog?category=${c.slug}`} className="text-brand-700 hover:text-brand-900">{c.name}</Link>
          </span>
        ))}
      </nav>
      <h1 className="mt-4 font-display text-3xl leading-tight font-light tracking-[-0.02em] text-stone-900 sm:text-5xl">{p.title}</h1>
      <p className="mt-3 text-sm text-stone-500">
        {[p.author_name && `By ${p.author_name}`, p.published_at && postDate(p.published_at)].filter(Boolean).join(' · ')}
      </p>
      {p.cover_url && (
        <img src={p.cover_url} alt={p.photos.find((ph) => ph.id === p.cover_photo_id)?.caption ?? ''}
          className="mt-6 aspect-[16/9] w-full rounded-(--card-radius) object-cover" />
      )}
      {p.excerpt && <p className="mt-6 text-lg leading-relaxed text-stone-800">{p.excerpt}</p>}
      <div className="mt-6">
        <BlogBody body={p.body} photos={p.photos} />
      </div>
      <div className="mt-12 border-t border-stone-200 pt-6">
        <Link to="/blog" className="text-sm font-medium text-brand-800 hover:text-brand-900">← More from the trail</Link>
      </div>
    </article>
  )
}
