import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import type { TrackPhoto } from '../../api/catalog.ts'
import { TrekSection } from './TrekSections.tsx'

/**
 * "Photos from the trail": one large photo with its caption ("Summit ridge at first light", "Kedarkantha summit ·
 * Day 4"), arrows, swipe and arrow keys, and a strip of thumbnails underneath.
 */
export function TrailPhotos({ id, photos, trekName }: { id: string; photos: TrackPhoto[]; trekName: string }) {
  const [index, setIndex] = useState(0)
  const swipeFrom = useRef<number | null>(null)
  const strip = useRef<HTMLUListElement>(null)
  if (photos.length === 0) return null

  const photo = photos[Math.min(index, photos.length - 1)]
  const go = (next: number) => {
    const i = (next + photos.length) % photos.length
    setIndex(i)
    strip.current?.children[i]?.scrollIntoView({ block: 'nearest', inline: 'nearest', behavior: 'smooth' })
  }
  const where = [photo.place, photo.day_number && `Day ${photo.day_number}`].filter(Boolean).join(' · ')
  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'ArrowLeft') go(index - 1)
    if (e.key === 'ArrowRight') go(index + 1)
  }
  const onPointerUp = (e: PointerEvent) => {
    if (swipeFrom.current === null) return
    const dx = e.clientX - swipeFrom.current
    swipeFrom.current = null
    if (Math.abs(dx) > 40) go(dx < 0 ? index + 1 : index - 1)
  }

  return (
    <TrekSection id={id} label="Photos from the trail" aside={`${index + 1} of ${photos.length}`}>
      <figure
        tabIndex={0}
        onKeyDown={onKey}
        onPointerDown={(e) => (swipeFrom.current = e.clientX)}
        onPointerUp={onPointerUp}
        className="relative overflow-hidden rounded-2xl bg-paper-200 outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
        aria-roledescription="carousel"
        aria-label={`${trekName} photos`}
      >
        <img
          key={photo.id}
          src={photo.url}
          alt={photo.caption ?? `${trekName}, photo ${index + 1} of ${photos.length}`}
          draggable={false}
          className="aspect-[16/10] w-full object-cover select-none"
        />
        <figcaption className="absolute inset-x-0 bottom-0 flex items-end justify-between gap-4 bg-gradient-to-t from-black/70 via-black/30 to-transparent px-4 pt-16 pb-4 text-white sm:px-5">
          <span className="min-w-0">
            {photo.caption && <span className="block truncate font-semibold sm:text-lg">{photo.caption}</span>}
            {where && <span className="block truncate text-sm text-white/80">{where}</span>}
          </span>
          {photos.length > 1 && (
            <span className="flex shrink-0 gap-2">
              <Arrow label="Previous photo" onClick={() => go(index - 1)} d="M12.5 5 7.5 10l5 5" />
              <Arrow label="Next photo" onClick={() => go(index + 1)} d="m7.5 5 5 5-5 5" />
            </span>
          )}
        </figcaption>
      </figure>
      {photos.length > 1 && (
        <ul ref={strip} className="mt-3 flex gap-2 overflow-x-auto pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          {photos.map((p, i) => (
            <li key={p.id} className="shrink-0">
              <button
                type="button"
                onClick={() => go(i)}
                aria-label={p.caption ?? `Photo ${i + 1}`}
                aria-current={i === index}
                className={`block overflow-hidden rounded-lg ring-2 transition ${
                  i === index ? 'ring-laterite-600' : 'ring-transparent opacity-75 hover:opacity-100'
                }`}
              >
                <img src={p.url} alt="" loading="lazy" className="aspect-[4/3] w-24 object-cover" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </TrekSection>
  )
}

function Arrow({ label, onClick, d }: { label: string; onClick: () => void; d: string }) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      onPointerDown={(e) => e.stopPropagation()}
      className="flex size-10 items-center justify-center rounded-full bg-white/90 text-stone-900 hover:bg-white"
    >
      <svg viewBox="0 0 20 20" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
        <path d={d} />
      </svg>
    </button>
  )
}
