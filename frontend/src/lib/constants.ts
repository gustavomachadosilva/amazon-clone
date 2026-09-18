import type { Review } from '../types/domain'

export const STORE_NAME = 'Mercatto'

// Curated subset of the real catalog categories shown as quick links in the header nav bar —
// the full department list only fits there for a handful of picks, so this narrows it to a
// diverse sample rather than dumping all of them in one row.
export const HEADER_HIGHLIGHT_CATEGORIES = [
  'Beauty & Personal Care',
  'Computers & Tablets',
  "Men's Clothing",
  "Women's Clothing",
  'Toys & Games',
]

export const RATING_WORD = ['Select a rating', 'I hate it', "I don't like it", "It's OK", 'I like it', 'I love it']

export const RATING_DISTRIBUTION = [
  { label: '5★', pct: 68 },
  { label: '4★', pct: 21 },
  { label: '3★', pct: 7 },
  { label: '2★', pct: 2 },
  { label: '1★', pct: 2 },
]

export const DELIVERY_DATE_LABEL = 'Thursday, August 13'

export const DEFAULT_ADDRESS = {
  street: '1578 Union Street, Apt 92',
  city: 'Seattle',
  state: 'WA',
  zip: '98104',
}

export const SHIPPING_OPTIONS = {
  STANDARD: 'Standard — 3 to 5 business days',
  EXPRESS: 'Express — arrives tomorrow',
  PICKUP: 'Pick up at a partner locker',
} as const

export const PAYMENT_OPTIONS = {
  CARD: 'Credit card ending in 4417',
  STORE: 'Store card — 5% back',
  GIFT: 'Gift card balance',
} as const

export const RELATED_REASONS = [
  'Highest rated in {category}',
  'Similar item at a lower price',
  'Most reviewed by customers like you',
  'Arrives tomorrow with this order',
]

export const ALSO_VIEWED_SHARES = [38, 24, 19, 14, 11, 9]

export const BASE_REVIEWS: Review[] = [
  {
    stars: 5,
    title: 'Better than I expected',
    author: 'Marcus T.',
    date: 'July 12, 2026',
    text: 'Arrived two days ahead of the estimate and the finish looks better than in the photos. I have used it daily for three weeks with no issues.',
    helpful: 34,
  },
  {
    stars: 4,
    title: 'Good value for the money',
    author: 'Julia P.',
    date: 'June 28, 2026',
    text: 'Does what it promises. One star off because the manual is English-only, but setup is simple enough without it.',
    helpful: 12,
  },
  {
    stars: 5,
    title: 'Holds up to professional use',
    author: 'Renato A.',
    date: 'June 3, 2026',
    text: 'Bought two for the workshop. Solid build, and seller support replied in under a day when I asked for an invoice.',
    helpful: 9,
  },
]
