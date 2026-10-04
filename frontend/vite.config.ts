import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

const FIRST_HERO = 'src/assets/hero/hero-1.jpg'

/**
 * Starts the first landing photo downloading from index.html, alongside the app's script, instead of only
 * once React has rendered the hero. Only on the home page: other pages never show it. In a build the link
 * points at the photo's hashed file; in dev, at the source file.
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
          {
            tag: 'script',
            injectTo: 'head-prepend',
            children: `if(location.pathname==='/'){var l=document.createElement('link');l.rel='preload';l.as='image';l.href=${JSON.stringify(href)};l.fetchPriority='high';document.head.appendChild(l)}`,
          },
        ]
      },
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), preloadFirstHero()],
  // VITE_* vars live in the repo-root .env alongside the backend's.
  envDir: '..',
})
