import type { Product } from '../services/api'

/**
 * brand, listPrice/discount, warranty and model number are now real Product fields (see
 * catalog.Product) — this file no longer derives them. What remains below is fabricated on
 * purpose: fast-delivery/delivery-estimate has no real source in this project (there is no
 * logistics/shipping module), so it stays a deterministic, clearly-labeled placeholder rather
 * than a random value, to avoid pretending there is real delivery data behind it.
 */

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
