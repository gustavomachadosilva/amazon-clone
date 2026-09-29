import { describe, expect, it } from 'vitest'
import { relatedReasonLabel } from './relatedReasons'

const current = { price: 100 }
const item = { price: 89.99, brand: 'Acme', category: 'Tools' }

describe('relatedReasonLabel', () => {
  it.each([
    ['SAME_BRAND', 'More from Acme'],
    ['LOWER_PRICE', 'Similar item, $10.01 less'],
    ['TOP_RATED_IN_CATEGORY', 'Highest rated in Tools'],
    ['HIGHER_RATED', 'Rated higher than this item'],
    ['SIMILAR_NAME', 'Similar to this item'],
    ['SAME_CATEGORY', 'Also in Tools'],
  ] as const)('%s → %s', (reason, label) => {
    expect(relatedReasonLabel(reason, current, item)).toBe(label)
  })

  it('returns null for a reason it does not know', () => {
    expect(relatedReasonLabel('MOST_VIEWED', current, item)).toBeNull()
  })

  it('returns null for SAME_BRAND when the item has no brand', () => {
    expect(relatedReasonLabel('SAME_BRAND', current, { ...item, brand: null })).toBeNull()
  })

  it('returns null for LOWER_PRICE when the item is not actually cheaper', () => {
    expect(relatedReasonLabel('LOWER_PRICE', current, { ...item, price: 100 })).toBeNull()
  })
})
