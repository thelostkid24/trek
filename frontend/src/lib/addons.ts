import type { TrackDetail } from '../api/catalog.ts'

export type AddonKey = 'insurance' | 'offloading' | 'transport'

export type OfferedAddon = { key: AddonKey; label: string; note: string; price: number }

/** The add-ons a trek offers, with their per-seat price; one without a price isn't offered (docs/TRD.md §7.6). */
export function addonsOffered(t: TrackDetail): OfferedAddon[] {
  const all: { key: AddonKey; label: string; note: string; price: number | null }[] = [
    { key: 'insurance', label: 'Trek insurance', note: 'covers the trek days', price: t.insurance_price_paise },
    { key: 'offloading', label: 'Bag offloading', note: 'a mule or porter carries your bag', price: t.offloading ? t.offloading_price_paise : null },
    { key: 'transport', label: 'Transport', note: t.pickup_drop ?? 'to and from the road head', price: t.transport_price_paise },
  ]
  return all.filter((a): a is OfferedAddon => a.price != null)
}
