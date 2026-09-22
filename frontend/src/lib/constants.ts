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
