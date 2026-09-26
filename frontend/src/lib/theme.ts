import { useState } from 'react'

export type Theme = 'light' | 'dark'

/** index.html sets `data-theme` on <html> before first paint; this reads and flips it. */
export function useTheme() {
  const [theme, setThemeState] = useState<Theme>(() =>
    document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light',
  )

  const setTheme = (next: Theme) => {
    document.documentElement.dataset.theme = next
    try {
      localStorage.setItem('theme', next)
    } catch {
      // Private mode or blocked storage: the choice lasts for this page only.
    }
    setThemeState(next)
  }

  return { theme, toggle: () => setTheme(theme === 'dark' ? 'light' : 'dark') }
}
