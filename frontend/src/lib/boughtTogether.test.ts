import { describe, expect, it } from 'vitest'
import type { BoughtTogether, Product, RelatedProduct } from '../services/api'
import { bundleCopy, bundleItemNote, withoutBundleItems } from './boughtTogether'

function product(id: number, overrides: Partial<Product> = {}): Product {
  return {
    id,
    name: `Product ${id}`,
    description: 'A product',
    price: 100,
    stockQuantity: 10,
    category: 'Tools',
    sellerId: 1,
    brand: null,
    warrantyMonths: null,
    modelNumber: null,
    listPrice: null,
    averageRating: 0,
    reviewCount: 0,
    ...overrides,
  }
}

function related(id: number): RelatedProduct {
  return { product: product(id), score: 5, primaryReason: 'SAME_CATEGORY', reasons: ['SAME_CATEGORY'] }
}

describe('bundleCopy', () => {
  it('calls a co-purchase bundle "Frequently bought together"', () => {
    expect(bundleCopy('CO_PURCHASE')).toEqual({ heading: 'Frequently bought together', caption: null })
  })

  it('never says "bought together" for the similarity fallback', () => {
    const copy = bundleCopy('SIMILAR')
    expect(copy.heading).toBe('Pairs well with this item')
    expect(copy.caption).toMatch(/not enough purchase history/i)
    expect(`${copy.heading} ${copy.caption}`).not.toMatch(/bought together/i)
  })
})

describe('bundleItemNote', () => {
  const current = { price: 100 }

  it('counts the customers for a co-purchased item', () => {
    expect(bundleItemNote({ product: product(2), timesBoughtTogether: 3, primaryReason: null }, current)).toBe(
      'Bought together by 3 customers',
    )
  })

  it('uses the similarity reason for a similar item', () => {
    expect(
      bundleItemNote({ product: product(2, { price: 80 }), timesBoughtTogether: null, primaryReason: 'LOWER_PRICE' }, current),
    ).toBe('Similar item, $20.00 less')
  })

  it('renders nothing without a count or a reason', () => {
    expect(bundleItemNote({ product: product(2), timesBoughtTogether: null, primaryReason: null }, current)).toBeNull()
  })
})

describe('withoutBundleItems', () => {
  const list = [related(2), related(3), related(4)]

  it('drops the products a similar bundle already shows', () => {
    const bundle: BoughtTogether = {
      source: 'SIMILAR',
      items: [{ product: product(3), timesBoughtTogether: null, primaryReason: 'SAME_CATEGORY' }],
    }
    expect(withoutBundleItems(list, bundle).map((r) => r.product.id)).toEqual([2, 4])
  })

  it('keeps everything for a co-purchase bundle or no bundle', () => {
    const bundle: BoughtTogether = {
      source: 'CO_PURCHASE',
      items: [{ product: product(3), timesBoughtTogether: 2, primaryReason: null }],
    }
    expect(withoutBundleItems(list, bundle)).toEqual(list)
    expect(withoutBundleItems(list, null)).toEqual(list)
  })
})
