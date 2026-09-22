import { useEffect, useRef, useState } from 'react'
import type { TrackPhoto } from '../../api/catalog.ts'

/** How many tiles the grid shows before "+N more" (the lightbox still pages through all). */
const TILES = 5

/**
 * Photos from past runs of the trek. Phones get a swipeable strip; wider screens a mosaic with the first photo large.
 * Any photo opens a full-screen viewer.
 */
export function TrekGallery({ photos, trekName }: { photos: TrackPhoto[]; trekName: string }) {
  const [open, setOpen] = useState<number | null>(null)
  if (photos.length === 0) return null

  const alt = (p: TrackPhoto, i: number) => p.caption ?? `${trekName}, photo ${i + 1} of ${photos.length}`
  const hidden = photos.length - TILES
  // The mosaic needs five photos to fill; fewer sit in an even grid.
  const mosaic = photos.length >= TILES

  return (
    <section aria-labelledby="gallery-heading">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id="gallery-heading" className="text-xs font-semibold tracking-[0.14em] text-stone-500 uppercase">
          From past treks
        </h2>
        <span className="text-xs text-stone-500">
          {photos.length} {photos.length === 1 ? 'photo' : 'photos'}
        </span>
      </div>

      {/* Phones: horizontal strip. */}
      <ul className="-mx-4 mt-3 flex snap-x snap-mandatory scroll-px-4 gap-3 overflow-x-auto px-4 pb-1 [scrollbar-width:none] sm:hidden [&::-webkit-scrollbar]:hidden">
        {photos.map((p, i) => (
          <li key={p.id} className="w-[80vw] max-w-sm shrink-0 snap-start">
            <Tile photo={p} alt={alt(p, i)} onOpen={() => setOpen(i)} className="aspect-[4/3]" />
            {p.caption && <p className="mt-1.5 truncate text-xs text-stone-600">{p.caption}</p>}
          </li>
        ))}
      </ul>

      {/* Wider screens: mosaic, first photo large. */}
      <ul
        className={`mt-3 hidden gap-2 sm:grid ${
          mosaic ? 'auto-rows-[9rem] grid-cols-4 lg:auto-rows-[10rem]' : photos.length === 1 ? 'grid-cols-1' : 'grid-cols-2'
        }`}
      >
        {photos.slice(0, TILES).map((p, i) => (
          <li key={p.id} className={`relative ${mosaic && i === 0 ? 'col-span-2 row-span-2' : ''}`}>
            <Tile
              photo={p}
              alt={alt(p, i)}
              onOpen={() => setOpen(i)}
              className={mosaic ? 'h-full' : photos.length === 1 ? 'aspect-[16/9]' : 'aspect-[4/3]'}
            />
            {i === TILES - 1 && hidden > 0 && (
              <span className="pointer-events-none absolute inset-0 flex items-center justify-center rounded-xl bg-black/45 text-lg font-medium text-white">
                +{hidden} more
              </span>
            )}
          </li>
        ))}
      </ul>

      {open !== null && (
        <Lightbox photos={photos} index={open} alt={alt} onIndex={setOpen} onClose={() => setOpen(null)} />
      )}
    </section>
  )
}

function Tile({ photo, alt, onOpen, className }: { photo: TrackPhoto; alt: string; onOpen: () => void; className: string }) {
  return (
    <button
      type="button"
      onClick={onOpen}
      className={`group block w-full overflow-hidden rounded-xl bg-stone-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 ${className}`}
    >
      <img
        src={photo.url}
        alt={alt}
        loading="lazy"
        decoding="async"
        className="size-full object-cover transition duration-300 group-hover:scale-[1.03]"
      />
    </button>
  )
}

function Lightbox({
  photos,
  index,
  alt,
  onIndex,
  onClose,
}: {
  photos: TrackPhoto[]
  index: number
  alt: (p: TrackPhoto, i: number) => string
  onIndex: (i: number) => void
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const touchX = useRef<number | null>(null)
  const photo = photos[index]
  const go = (step: number) => onIndex((index + step + photos.length) % photos.length)

  useEffect(() => {
    dialog.current?.showModal()
  }, [])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') go(1)
      if (e.key === 'ArrowLeft') go(-1)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  return (
    <dialog
      ref={dialog}
      onClose={onClose}
      onClick={(e) => e.target === e.currentTarget && dialog.current?.close()}
      aria-label="Trek photos"
      className="m-0 h-dvh max-h-none w-screen max-w-none bg-black/95 p-0 text-white backdrop:bg-black/80"
    >
      <div
        className="flex h-full flex-col"
        onTouchStart={(e) => (touchX.current = e.touches[0].clientX)}
        onTouchEnd={(e) => {
          if (touchX.current === null) return
          const dx = e.changedTouches[0].clientX - touchX.current
          if (Math.abs(dx) > 50) go(dx < 0 ? 1 : -1)
          touchX.current = null
        }}
      >
        <div className="flex items-center justify-between px-4 py-3 text-sm">
          <span className="text-white/70">
            {index + 1} / {photos.length}
          </span>
          <button
            type="button"
            onClick={() => dialog.current?.close()}
            className="rounded-full px-3 py-1.5 text-white/90 hover:bg-white/10"
          >
            Close ✕
          </button>
        </div>
        <div
          className="relative flex min-h-0 flex-1 items-center justify-center px-2 sm:px-16"
          onClick={(e) => e.target === e.currentTarget && dialog.current?.close()}
        >
          <img key={photo.id} src={photo.url} alt={alt(photo, index)} className="max-h-full max-w-full object-contain" />
          {photos.length > 1 && (
            <>
              <NavButton label="Previous photo" className="left-3" onClick={() => go(-1)}>
                ←
              </NavButton>
              <NavButton label="Next photo" className="right-3" onClick={() => go(1)}>
                →
              </NavButton>
            </>
          )}
        </div>
        <p className="min-h-12 px-4 py-3 text-center text-sm text-white/80">{photo.caption}</p>
      </div>
    </dialog>
  )
}

function NavButton({
  label,
  className,
  onClick,
  children,
}: {
  label: string
  className: string
  onClick: () => void
  children: string
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      className={`absolute top-1/2 hidden size-11 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 text-lg hover:bg-white/20 sm:flex ${className}`}
    >
      {children}
    </button>
  )
}
