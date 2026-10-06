// Fails when an inline <script> in dist/**/*.html isn't allowed by the CSP in firebase.json. Inline scripts run
// only if their sha256 is listed in script-src, so a changed or new one would be blocked once the CSP enforces.
// Runs after `npm run build` (and prerender) in CI: `npm run check:csp`. See docs/TRD.md §4.
import { createHash } from 'node:crypto'
import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'

const firebase = JSON.parse(await readFile('firebase.json', 'utf8'))
const policy = firebase.hosting.headers
  .flatMap((h) => h.headers)
  .find((h) => /^Content-Security-Policy(-Report-Only)?$/.test(h.key))?.value
if (!policy) throw new Error('firebase.json has no Content-Security-Policy header')
const scriptSrc = policy.split(';').map((d) => d.trim()).find((d) => d.startsWith('script-src ')) ?? ''
const allowed = new Set(scriptSrc.match(/'sha256-[^']+'/g) ?? [])

async function* htmlFiles(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) yield* htmlFiles(path)
    else if (entry.name.endsWith('.html')) yield path
  }
}

const missing = new Map()
let checked = 0
for await (const file of htmlFiles('dist')) {
  const html = await readFile(file, 'utf8')
  for (const [, attrs, body] of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
    // External scripts are allowed by origin; JSON-LD and other data blocks never execute.
    if (/\bsrc\s*=/i.test(attrs)) continue
    const type = attrs.match(/\btype\s*=\s*["']?([^"'\s>]+)/i)?.[1]?.toLowerCase()
    if (type && type !== 'module' && !type.includes('javascript')) continue
    checked++
    const hash = `'sha256-${createHash('sha256').update(body, 'utf8').digest('base64')}'`
    if (!allowed.has(hash)) missing.set(hash, file)
  }
}

if (missing.size > 0) {
  for (const [hash, file] of missing) console.error(`check-csp: ${file} has an inline script not in script-src: ${hash}`)
  process.exit(1)
}
console.log(`check-csp: ${checked} inline scripts, all allowed`)
