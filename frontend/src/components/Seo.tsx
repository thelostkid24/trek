import type { ReactNode } from 'react'
import { SITE_ORIGIN } from '../lib/siteLinks.ts'

const SITE_NAME = 'The Empty Valley'
const DEFAULT_TITLE = `${SITE_NAME} — Small batches. Guaranteed departures.`
const DEFAULT_DESCRIPTION = 'Small-batch Himalayan treks in Uttarakhand, led by certified local guides you choose. Ten trekkers at most, and every departure is guaranteed.'
const DEFAULT_IMAGE = `${SITE_ORIGIN}/hero.jpg`
/** Roughly what Google shows of a description before cutting it off. */
const DESCRIPTION_MAX = 155

/** The first paragraph on one line, cut at a word with "…" when it's longer than search results show. */
function metaDescription(text: string): string {
  const first = text.trim().split(/\n\s*\n/)[0].replace(/\s+/g, ' ')
  if (first.length <= DESCRIPTION_MAX) return first
  const cut = first.slice(0, DESCRIPTION_MAX - 1)
  const space = cut.lastIndexOf(' ')
  return (space > 0 ? cut.slice(0, space) : cut).replace(/[\s,;:.–—-]+$/, '') + '…'
}

type Props = {
  /** Page name; the site name is appended. Omit for the home page. */
  title?: string
  /** Use `title` as it is, without the site name (a long blog post title). */
  bareTitle?: boolean
  description?: string
  /** Canonical path, e.g. `/treks/rajmachi`. Omit on pages that shouldn't be indexed. */
  path?: string
  /** Absolute URL of the link-preview image; the landing photo by default. */
  image?: string
  /** Structured data (schema.org) describing the page to search engines, rendered as JSON-LD. */
  jsonLd?: object
  /** Keeps the page out of search results (private, auth and not-found pages). */
  noindex?: boolean
  /** A blog post: shared as an article, with when it was published and last changed. */
  article?: { published: string | null; modified: string }
}

/**
 * The page's head tags (docs/TRD.md §7.16). React 19 hoists these into <head>; render exactly one per page.
 * index.html's own head tags are for link previews only and main.tsx removes them on load, so every route renders this.
 */
export function Seo({ title, bareTitle = false, description: text = DEFAULT_DESCRIPTION, path, image = DEFAULT_IMAGE, jsonLd, noindex = false, article }: Props) {
  const description = metaDescription(text) || DEFAULT_DESCRIPTION
  const fullTitle = title ? (bareTitle ? title : `${title} · ${SITE_NAME}`) : DEFAULT_TITLE
  const url = path === undefined ? undefined : SITE_ORIGIN + path
  return (
    <>
      <title>{fullTitle}</title>
      <meta name="description" content={description} />
      {noindex && <meta name="robots" content="noindex" />}
      {url && <link rel="canonical" href={url} />}
      <meta property="og:site_name" content={SITE_NAME} />
      <meta property="og:type" content={article ? 'article' : 'website'} />
      {article?.published && <meta property="article:published_time" content={article.published} />}
      {article && <meta property="article:modified_time" content={article.modified} />}
      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={description} />
      <meta property="og:image" content={image} />
      {url && <meta property="og:url" content={url} />}
      <meta name="twitter:card" content="summary_large_image" />
      {/* Inline JSON-LD isn't hoisted or run; search engines read it wherever it sits. `<` is escaped so text from
          the API can't close the script tag. */}
      {jsonLd && (
        <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replace(/</g, '\\u003c') }} />
      )}
    </>
  )
}

/** Pages behind sign-in or mid-flow: a tab title, and kept out of search results (robots.txt disallows them too). */
export function Private({ title, children }: { title: string; children: ReactNode }) {
  return (
    <>
      <Seo title={title} noindex />
      {children}
    </>
  )
}
