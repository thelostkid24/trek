import type { TrekPage } from '../api/catalog.ts'
import { SITE_ORIGIN } from './siteLinks.ts'

// schema.org data for search engines (docs/TRD.md §7.16). Google's Event markup excludes travel packages, so a trek
// is a TouristTrip. TouristTrip gets no rich result, but breadcrumbs do.

const ORGANIZATION = { '@type': 'Organization', name: 'The Empty Valley', url: `${SITE_ORIGIN}/` }

/** A trek page: the trek with each upcoming departure as an offer, plus its breadcrumb trail. */
export function trekJsonLd({ track, departures }: TrekPage): object {
  const url = `${SITE_ORIGIN}/treks/${track.slug}`
  return {
    '@context': 'https://schema.org',
    '@graph': [
      {
        '@type': 'TouristTrip',
        name: track.name,
        description: track.summary,
        url,
        ...(track.photos.length > 0 && { image: track.photos.slice(0, 5).map((p) => p.url) }),
        provider: ORGANIZATION,
        ...(track.itinerary.length > 0 && {
          itinerary: {
            '@type': 'ItemList',
            itemListElement: track.itinerary.map((d) => ({ '@type': 'ListItem', position: d.day, name: d.summary })),
          },
        }),
        offers: departures.map((d) => ({
          '@type': 'Offer',
          url: `${SITE_ORIGIN}/departures/${d.id}`,
          price: (d.price_paise / 100).toFixed(2),
          priceCurrency: 'INR',
          availability: d.bookable && d.seats_left > 0 ? 'https://schema.org/InStock' : 'https://schema.org/SoldOut',
          validThrough: d.start_date,
          description: `${d.start_date} to ${d.end_date}`,
        })),
      },
      {
        '@type': 'BreadcrumbList',
        itemListElement: [
          { '@type': 'ListItem', position: 1, name: 'Treks', item: `${SITE_ORIGIN}/treks` },
          { '@type': 'ListItem', position: 2, name: track.name, item: url },
        ],
      },
    ],
  }
}
