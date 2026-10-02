import type { ReactNode } from 'react'
import { SITE_ORIGIN } from '../lib/siteLinks.ts'

const SITE_NAME = 'The Empty Valley'
const DEFAULT_TITLE = `${SITE_NAME} — Small batches. Guaranteed departures.`
const DEFAULT_DESCRIPTION = 'Small-batch treks with vetted local guides and guaranteed departures.'

type Props = {
  /** Page name; the site name is appended. Omit for the home page. */
  title?: string
  description?: string
  /** Canonical path, e.g. `/treks/rajmachi`. Omit on pages that shouldn't be indexed. */
  path?: string
  /** Keeps the page out of search results (private, auth and not-found pages). */
  noindex?: boolean
}

/**
 * The page's head tags (docs/TRD.md §7.16). React 19 hoists these into <head>; render exactly one per page.
 * index.html has no <title> or description of its own, so every route renders this.
 */
export function Seo({ title, description = DEFAULT_DESCRIPTION, path, noindex = false }: Props) {
  const fullTitle = title ? `${title} · ${SITE_NAME}` : DEFAULT_TITLE
  const url = path === undefined ? undefined : SITE_ORIGIN + path
  return (
    <>
      <title>{fullTitle}</title>
      <meta name="description" content={description} />
      {noindex && <meta name="robots" content="noindex" />}
      {url && <link rel="canonical" href={url} />}
      <meta property="og:site_name" content={SITE_NAME} />
      <meta property="og:type" content="website" />
      <meta property="og:title" content={fullTitle} />
      <meta property="og:description" content={description} />
      <meta property="og:image" content={`${SITE_ORIGIN}/hero.jpg`} />
      {url && <meta property="og:url" content={url} />}
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
