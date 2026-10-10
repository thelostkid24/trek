// Writes a copy of dist/index.html for every public page, with that page's own title, description, canonical and
// link-preview tags in the static-head block: the fixed pages (/treks, /faqs, …), every trek (dist/treks/<slug>.html),
// every guide (dist/guides/<id>.html) and every published blog post (dist/blog/<slug>.html). Link-preview fetchers (WhatsApp, Facebook, X) don't run JavaScript, and
// search crawlers see these tags before rendering, so each page reads as itself from the first fetch. Firebase serves
// these files before its catch-all rewrite (cleanUrls in firebase.json maps /faqs to faqs.html). See docs/TRD.md §7.16.
//
// Runs after `npm run build` in the deploy workflow, against the live API: `npm run prerender`. A trek or guide added
// later (or a post published later) gets the site-wide preview until the next deploy.
import { mkdir, readFile, writeFile } from 'node:fs/promises'

const SITE_ORIGIN = 'https://theemptyvalley.com'
const SITE_NAME = 'The Empty Valley'
const DEFAULT_IMAGE = `${SITE_ORIGIN}/hero.jpg`
const DESCRIPTION_MAX = 155
const START = '<!-- static-head:start -->'
const END = '<!-- static-head:end -->'

// The fixed public pages, mirroring the <Seo> props in TreksPage.tsx, GuidesPage.tsx, BlogPages.tsx and
// InfoPages.tsx; keep them in step. "Last updated" dates are left out of the legal pages' descriptions so this file
// needn't change with them.
// The home page is dist/index.html itself, which is also the fallback for every other route, so it keeps the
// site-wide head and gets no canonical until <Seo> renders.
const STATIC_PAGES = [
  {
    path: '/treks',
    title: 'Himalayan treks in Uttarakhand',
    description: 'Every Himalayan trek we run in Uttarakhand, with upcoming dates, difficulty and the local guide leading each departure.',
  },
  {
    path: '/guides',
    title: 'Our trek guides',
    description: 'Meet the local mountain guides who lead our Himalayan treks in Uttarakhand: their certifications, the treks they know and what trekkers say.',
  },
  {
    path: '/blog',
    title: 'Blog',
    description: 'Trail notes from the Himalaya: trek guides, snow and season updates, and stories from our small-batch treks in Uttarakhand.',
  },
  {
    path: '/vision',
    title: 'Our vision',
    description: 'Why we built The Empty Valley: certified local guides run their own Himalayan treks, keep most of what you pay, and you choose who leads you.',
  },
  {
    path: '/faqs',
    title: 'FAQs',
    description: 'Answers before you book a Himalayan trek in Uttarakhand: choosing your guide, group size, safety, experience needed, payments and cancellations.',
  },
  {
    path: '/cancellations',
    title: 'Cancellations & refunds',
    description: 'You can cancel a paid booking from its page in My treks, any time before the start date. The whole booking is cancelled together.',
  },
  {
    path: '/contact',
    title: 'Contact',
    description: 'Reach The Empty Valley by email, phone or WhatsApp about a trek or a booking. Monday to Saturday, 10 am – 7 pm IST.',
  },
  { path: '/terms', title: 'Terms of use', description: 'These terms apply when you use The Empty Valley or book a trek with us.' },
  {
    path: '/privacy',
    title: 'Privacy policy',
    description: 'How The Empty Valley collects and uses your personal data, under India’s Digital Personal Data Protection Act, 2023.',
  },
  { path: '/cookies', title: 'Cookie policy', description: 'What The Empty Valley stores in your browser, why, and for how long.' },
  { path: '/credits', title: 'Credits', description: 'The work of other people this site is built with.' },
]

const api = process.env.VITE_API_BASE_URL?.replace(/\/$/, '')
if (!api) throw new Error('VITE_API_BASE_URL is not set')

const html = await readFile('dist/index.html', 'utf8')
const start = html.indexOf(START)
const end = html.indexOf(END)
if (start < 0 || end < start) throw new Error('dist/index.html has no static-head markers')

async function get(path) {
  const res = await fetch(`${api}${path}`)
  if (!res.ok) throw new Error(`GET ${path}: ${res.status}`)
  return res.json()
}

const escape = (s) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

/** Same as metaDescription in src/components/Seo.tsx: the first paragraph, cut at a word with "…". */
function metaDescription(text) {
  const first = text.trim().split(/\n\s*\n/)[0].replace(/\s+/g, ' ')
  if (first.length <= DESCRIPTION_MAX) return first
  const cut = first.slice(0, DESCRIPTION_MAX - 1)
  const space = cut.lastIndexOf(' ')
  return (space > 0 ? cut.slice(0, space) : cut).replace(/[\s,;:.–—-]+$/, '') + '…'
}

// Same tags as <Seo> (src/components/Seo.tsx).
function head({ title, description: text, path, image = DEFAULT_IMAGE }) {
  const fullTitle = escape(`${title} · ${SITE_NAME}`)
  const description = escape(metaDescription(text))
  const url = SITE_ORIGIN + path
  return [
    `<title data-static-head>${fullTitle}</title>`,
    `<meta data-static-head name="description" content="${description}" />`,
    `<link data-static-head rel="canonical" href="${url}" />`,
    `<meta data-static-head property="og:site_name" content="${SITE_NAME}" />`,
    `<meta data-static-head property="og:type" content="website" />`,
    `<meta data-static-head property="og:title" content="${fullTitle}" />`,
    `<meta data-static-head property="og:description" content="${description}" />`,
    `<meta data-static-head property="og:image" content="${escape(image)}" />`,
    `<meta data-static-head property="og:url" content="${url}" />`,
    `<meta data-static-head name="twitter:card" content="summary_large_image" />`,
  ].join('\n    ')
}

/** Writes dist/<path>.html, which cleanUrls serves at <path>. */
async function write(page) {
  await writeFile(`dist${page.path}.html`, html.slice(0, start + START.length) + '\n    ' + head(page) + '\n    ' + html.slice(end))
}

for (const page of STATIC_PAGES) await write(page)

// Same title, description and canonical as TrekPage.tsx. "Trek" is in the title because that's the word people search
// with ("Kedarkantha trek").
await mkdir('dist/treks', { recursive: true })
let treks = 0
for (const trek of (await get('/api/public/tracks')).items) {
  // Slugs become file names; anything unexpected keeps the site-wide preview.
  if (!/^[a-z0-9-]+$/.test(trek.slug)) continue
  const name = /\btrek\b/i.test(trek.name) ? trek.name : `${trek.name} Trek`
  await write({ title: `${name}, ${trek.region}`, description: trek.summary, path: `/treks/${trek.slug}`, image: trek.cover_url ?? undefined })
  treks++
}

// Same title, description and canonical as GuidePage.tsx. The list has no bio, so each profile is fetched.
await mkdir('dist/guides', { recursive: true })
let guides = 0
for (const { id } of (await get('/api/public/guides')).items) {
  if (!/^[0-9a-f-]{36}$/.test(id)) continue
  const guide = await get(`/api/public/guides/${id}`)
  const name = guide.full_name ?? 'Local guide'
  const description = guide.bio || `${name} leads small-batch treks with ${SITE_NAME}.`
  await write({ title: `${name}, trek guide`, description, path: `/guides/${guide.id}` })
  guides++
}

// Same title, description, canonical and image as BlogPostPage in BlogPages.tsx: the excerpt, else the body's first
// paragraph without markup (plainOpening in src/lib/blogBody.ts).
function plainOpening(body) {
  const para = []
  for (const raw of body.split(/\r?\n/)) {
    const line = raw.trim()
    const prose = line && !/^(#{2,3} |[-*] |!\[)/.test(line)
    if (prose) para.push(line)
    else if (para.length) break
  }
  return para.join(' ').replace(/\*\*(.+?)\*\*/g, '$1').replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
}
await mkdir('dist/blog', { recursive: true })
// Category pages in the Blog menu (3+ posts; the rest are noindex), with the same title as Category in BlogPages.tsx.
let categories = 0
for (const top of (await get('/api/public/blog/categories')).items) {
  const pages = [
    ...(top.in_menu ? [{ title: top.name, c: top, path: `/blog/${top.slug}` }] : []),
    ...top.subcategories.filter((s) => s.in_menu).map((s) => ({ title: `${s.name} · ${top.name}`, c: s, path: `/blog/${top.slug}/${s.slug}` })),
  ]
  if (pages.length > 1) await mkdir(`dist/blog/${top.slug}`, { recursive: true })
  for (const { title, c, path } of pages) {
    await write({ title, description: c.description ?? `${c.name}: articles from The Empty Valley's trek journal.`, path })
    categories++
  }
}
let posts = 0
for (const { slug } of (await get('/api/public/blog/posts')).items) {
  if (!/^[a-z0-9-]+$/.test(slug)) continue
  const post = await get(`/api/public/blog/posts/${slug}`)
  const description = post.excerpt || plainOpening(post.body) || STATIC_PAGES.find((p) => p.path === '/blog').description
  await write({ title: post.title, description, path: `/blog/${slug}`, image: post.cover_url ?? undefined })
  posts++
}

console.log(`prerender: wrote ${STATIC_PAGES.length} fixed pages, ${treks} trek pages, ${guides} guide pages, ${categories} blog categories, ${posts} blog posts`)
