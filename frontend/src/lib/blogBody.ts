// A post's body (docs/TRD.md §7.19), written in a small plain-text format the admin page explains:
//   blank line        new paragraph (a single line break stays a line break)
//   ## Heading        section heading (### for a smaller one)
//   - item            bullet list
//   ![caption](url)   one of the post's own photos, on a line of its own; any other image is skipped
//   **bold**  [text](https://… or /treks/…)
// Rendered by components/blog/BlogBody.tsx; scripts/prerender.mjs mirrors plainOpening.

export type Block =
  | { kind: 'p'; lines: string[] }
  | { kind: 'h2' | 'h3'; text: string }
  | { kind: 'ul'; items: string[] }
  | { kind: 'img'; caption: string; url: string }

const IMAGE = /^!\[([^\]]*)\]\((\S+)\)$/

export function parseBody(body: string): Block[] {
  const blocks: Block[] = []
  let para: string[] = []
  let list: string[] = []
  const flush = () => {
    if (para.length) blocks.push({ kind: 'p', lines: para })
    if (list.length) blocks.push({ kind: 'ul', items: list })
    para = []
    list = []
  }
  for (const raw of body.split(/\r?\n/)) {
    const line = raw.trim()
    const image = line.match(IMAGE)
    if (!line) {
      flush()
    } else if (line.startsWith('### ') || line.startsWith('## ')) {
      flush()
      const h3 = line.startsWith('### ')
      blocks.push({ kind: h3 ? 'h3' : 'h2', text: line.slice(h3 ? 4 : 3).trim() })
    } else if (image) {
      flush()
      blocks.push({ kind: 'img', caption: image[1].trim(), url: image[2] })
    } else if (/^[-*] /.test(line)) {
      if (para.length) flush()
      list.push(line.slice(2).trim())
    } else {
      if (list.length) flush()
      para.push(line)
    }
  }
  flush()
  return blocks
}

/** The body's opening words without markup, for cards and descriptions when a post has no excerpt. */
export function plainOpening(body: string): string {
  const first = parseBody(body).find((b) => b.kind === 'p')
  return first?.kind === 'p' ? first.lines.join(' ').replace(/\*\*(.+?)\*\*/g, '$1').replace(/\[([^\]]+)\]\([^)]+\)/g, '$1') : ''
}
