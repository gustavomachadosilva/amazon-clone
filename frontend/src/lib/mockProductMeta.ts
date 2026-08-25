import type { Product } from '../services/api'

/**
 * The real backend Product has no brand/list-price/rating/delivery/model-number/warranty
 * fields — these are computed deterministically from real fields so the same product
 * always shows the same numbers, instead of inventing random catalogue data.
 */

export function deriveBrandLabel(product: Product): string {
  return `Seller #${product.sellerId}`
}

export function deriveListPrice(product: Product): number {
  const markup = 1.15 + (product.id % 4) * 0.05
  return Math.round(product.price * markup * 100) / 100
}

export function deriveDiscountPct(product: Product): number {
  const listPrice = deriveListPrice(product)
  if (listPrice <= product.price) return 0
  return Math.round((1 - product.price / listPrice) * 100)
}

export function deriveStockLabel(product: Product): string {
  if (product.stockQuantity <= 0) return 'Out of stock'
  if (product.stockQuantity <= 5) return `Only ${product.stockQuantity} left in stock`
  return 'In Stock'
}

export function deriveFastDelivery(product: Product): boolean {
  return product.id % 2 === 0
}

export function deriveDeliveryLabel(product: Product): string {
  return deriveFastDelivery(product) ? 'Arrives tomorrow' : 'Free delivery Thursday, August 13'
}

export function deriveModelNumber(product: Product): string {
  return `MCT-${product.id}`
}

export const WARRANTY_LABEL = '12-month limited warranty'
