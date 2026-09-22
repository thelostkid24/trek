import type { ReactNode } from 'react'
import { revealClass, useInView } from '../lib/reveal.ts'

/** Fades its children up the first time they scroll into view. */
export function Reveal({ children, className = '', delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  const { ref, inView, settled } = useInView<HTMLDivElement>()
  return (
    <div ref={ref} className={`${revealClass(inView)} ${className}`} style={{ transitionDelay: settled ? undefined : `${delay}ms` }}>
      {children}
    </div>
  )
}
