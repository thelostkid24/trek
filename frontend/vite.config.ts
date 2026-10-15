import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { defineConfig, type Plugin } from 'vite'

const FIRST_HERO = 'src/assets/hero/hero-1.jpg'

/**
 * Starts the first landing photo downloading from index.html, alongside the app's script, instead of only
 * once React has rendered the hero. Only on the home page: other pages never show it. In a build the link
 * points at the photo's hashed file; in dev, at the source file. The URL rides in a meta tag so the inline
 * script never changes and its CSP hash in firebase.json stays valid (scripts/check-csp.mjs).
 */
function preloadFirstHero(): Plugin {
  return {
    name: 'preload-first-hero',
    transformIndexHtml: {
      order: 'post',
      handler(_html, ctx) {
        let href = `/${FIRST_HERO}`
        for (const out of Object.values(ctx.bundle ?? {})) {
          if (out.type === 'asset' && out.originalFileNames?.some((f) => f.endsWith(FIRST_HERO))) {
            href = `/${out.fileName}`
          }
        }
        return [
          { tag: 'meta', attrs: { name: 'hero-preload', content: href }, injectTo: 'head-prepend' },
          {
            tag: 'script',
            injectTo: 'head-prepend',
            children: `if(location.pathname==='/'){var h=document.querySelector('meta[name=hero-preload]');if(h){var l=document.createElement('link');l.rel='preload';l.as='image';l.href=h.content;l.fetchPriority='high';document.head.appendChild(l)}}`,
          },
        ]
      },
    },
  }
}

/**
 * Writes dist/app.html, the page Firebase serves for every route without a file of its own (firebase.json rewrite):
 * index.html minus the home page's canonical link. Otherwise every unknown or not-yet-prerendered URL would tell
 * Google it's the home page, and Google could pick any of them (e.g. /vision) to show for the brand search.
 */
function spaFallback(): Plugin {
  return {
    name: 'spa-fallback',
    apply: 'build',
    async writeBundle({ dir = 'dist' }) {
      const html = await readFile(join(dir, 'index.html'), 'utf8')
      const fallback = html.replace(/^\s*<link data-static-head rel="canonical"[^>]*>\n/m, '')
      if (fallback === html) throw new Error('spa-fallback: no canonical link in index.html')
      await writeFile(join(dir, 'app.html'), fallback)
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), preloadFirstHero(), spaFallback()],
  // VITE_* vars live in the repo-root .env alongside the backend's.
  envDir: '..',
})
