// Writes dist/treks/<slug>.html for every trek in the public catalog: a copy of dist/index.html whose link-preview
// block (between the static-head markers) names that trek and shows its cover photo. WhatsApp and other preview
// fetchers don't run JavaScript, so this is the only way they can show a trek's own name and photo. Firebase serves
// these files before its catch-all rewrite (cleanUrls in firebase.json maps /treks/<slug> to them). See docs/TRD.md §7.16.
//
// Runs after `npm run build` in the deploy workflow, against the live API: `npm run prerender`. A trek added later
// gets the site-wide preview until the next deploy.
import { mkdir, readFile, writeFile } from 'node:fs/promises'

const SITE_ORIGIN = 'https://theemptyvalley.com'
const SITE_NAME = 'The Empty Valley'
const START = '<!-- static-head:start -->'
const END = '<!-- static-head:end -->'

const api = process.env.VITE_API_BASE_URL?.replace(/\/$/, '')
if (!api) throw new Error('VITE_API_BASE_URL is not set')

const html = await readFile('dist/index.html', 'utf8')
const start = html.indexOf(START)
const end = html.indexOf(END)
if (start < 0 || end < start) throw new Error('dist/index.html has no static-head markers')

const res = await fetch(`${api}/api/public/tracks`)
if (!res.ok) throw new Error(`GET /api/public/tracks: ${res.status}`)
const { items } = await res.json()

const escape = (s) => s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

// Same title, description and canonical as the trek page's <Seo> (src/pages/TrekPage.tsx).
function head(trek) {
  const title = escape(`${trek.name}, ${trek.region} · ${SITE_NAME}`)
  const description = escape(trek.summary)
  const url = `${SITE_ORIGIN}/treks/${trek.slug}`
  const image = escape(trek.cover_url ?? `${SITE_ORIGIN}/hero.jpg`)
  return [
    `<title data-static-head>${title}</title>`,
    `<meta data-static-head name="description" content="${description}" />`,
    `<link data-static-head rel="canonical" href="${url}" />`,
    `<meta data-static-head property="og:site_name" content="${SITE_NAME}" />`,
    `<meta data-static-head property="og:type" content="website" />`,
    `<meta data-static-head property="og:title" content="${title}" />`,
    `<meta data-static-head property="og:description" content="${description}" />`,
    `<meta data-static-head property="og:image" content="${image}" />`,
    `<meta data-static-head property="og:url" content="${url}" />`,
    `<meta data-static-head name="twitter:card" content="summary_large_image" />`,
  ].join('\n    ')
}

await mkdir('dist/treks', { recursive: true })
let written = 0
for (const trek of items) {
  // Slugs become file names; anything unexpected keeps the site-wide preview.
  if (!/^[a-z0-9-]+$/.test(trek.slug)) continue
  const page = html.slice(0, start + START.length) + '\n    ' + head(trek) + '\n    ' + html.slice(end)
  await writeFile(`dist/treks/${trek.slug}.html`, page)
  written++
}
console.log(`prerender: wrote ${written} trek pages`)
