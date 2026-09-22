import { useEffect, useState } from 'react'

/**
 * `inView` turns true once the element scrolls into view and stays true. `settled` follows
 * about a second later, when entrance delays should be dropped so hover effects stay snappy.
 */
export function useInView<T extends Element>() {
  // A callback ref, so an element that mounts later (after data loads) still gets observed.
  const [el, ref] = useState<T | null>(null)
  const [inView, setInView] = useState(false)
  const [settled, setSettled] = useState(false)
  useEffect(() => {
    if (!inView) return
    const t = window.setTimeout(() => setSettled(true), 1200)
    return () => window.clearTimeout(t)
  }, [inView])
  useEffect(() => {
    if (!el || inView) return
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setInView(true)
          io.disconnect()
        }
      },
      { rootMargin: '0px 0px -10% 0px' },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [el, inView])
  return { ref, inView, settled }
}

/**
 * Classes for a fade-and-rise entrance. Only applied under `motion-safe`, so
 * reduced-motion users see content in place from the start.
 */
export function revealClass(shown: boolean) {
  return `motion-safe:transition motion-safe:duration-700 motion-safe:ease-out ${
    shown ? '' : 'motion-safe:translate-y-6 motion-safe:opacity-0'
  }`
}

/** Entrance delay for the i-th item of a staggered group, dropped once it has settled. */
export function staggerStyle({ inView, settled }: { inView: boolean; settled: boolean }, delayMs: number) {
  return inView && !settled ? { transitionDelay: `${delayMs}ms` } : undefined
}
