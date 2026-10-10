import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { BlogPhoto } from '../../api/blog.ts'
import { parseBody } from '../../lib/blogBody.ts'

// Renders a post's body (format: lib/blogBody.ts). Nothing is ever rendered as HTML, so the body can't inject markup.

/** **bold** and [text](link); links go to http(s) URLs (new tab) or paths on this site. */
function inline(text: string): ReactNode[] {
  const out: ReactNode[] = []
  const pattern = /\*\*(.+?)\*\*|\[([^\]]+)\]\(([^)\s]+)\)/g
  let last = 0
  for (const m of text.matchAll(pattern)) {
    const at = m.index ?? 0
    if (at > last) out.push(text.slice(last, at))
    const key = `${at}`
    if (m[1] !== undefined) {
      out.push(<strong key={key} className="font-semibold text-stone-900">{m[1]}</strong>)
    } else {
      const [label, href] = [m[2], m[3]]
      const className = 'font-medium text-brand-800 underline underline-offset-2 hover:text-brand-900'
      if (href.startsWith('/') && !href.startsWith('//')) {
        out.push(<Link key={key} to={href} className={className}>{label}</Link>)
      } else if (/^https?:\/\//i.test(href)) {
        out.push(<a key={key} href={href} target="_blank" rel="noopener noreferrer" className={className}>{label}</a>)
      } else {
        out.push(label)
      }
    }
    last = at + m[0].length
  }
  if (last < text.length) out.push(text.slice(last))
  return out
}

export function BlogBody({ body, photos }: { body: string; photos: BlogPhoto[] }) {
  const known = new Map(photos.map((p) => [p.url, p]))
  return (
    <div className="space-y-5 text-[1.05rem] leading-relaxed text-stone-700">
      {parseBody(body).map((b, i) => {
        switch (b.kind) {
          case 'h2':
            return <h2 key={i} className="pt-4 font-display text-2xl font-medium text-stone-900">{inline(b.text)}</h2>
          case 'h3':
            return <h3 key={i} className="pt-2 font-display text-xl font-medium text-stone-900">{inline(b.text)}</h3>
          case 'ul':
            return (
              <ul key={i} className="list-disc space-y-1.5 pl-6 marker:text-laterite-500">
                {b.items.map((item, j) => <li key={j}>{inline(item)}</li>)}
              </ul>
            )
          case 'img': {
            const photo = known.get(b.url)
            if (!photo) return null
            const caption = b.caption || photo.caption || ''
            return (
              <figure key={i} className="py-2">
                <img src={photo.url} alt={caption} loading="lazy" className="w-full rounded-(--card-radius) object-cover" />
                {caption && <figcaption className="mt-2 text-center text-sm text-stone-500">{caption}</figcaption>}
              </figure>
            )
          }
          default:
            return (
              <p key={i}>
                {b.lines.map((line, j) => (
                  <span key={j}>
                    {j > 0 && <br />}
                    {inline(line)}
                  </span>
                ))}
              </p>
            )
        }
      })}
    </div>
  )
}
