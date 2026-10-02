import type { Product } from '../services/api'
import { getStandardDeliveryLabel } from './deliveryDate'

/**
 * brand, listPrice/discount, warranty and model number are now real Product fields (see
 * catalog.Product) — this file no longer derives them. The delivery estimate below has no
 * per-product source in this project (there is no logistics/shipping module), so every product
 * shows the same standard-delivery estimate; the old fake "Arrives tomorrow" badge (and the
 * search filter built on it) was removed rather than pretending there is real data behind it.
 */

export function deriveStockLabel(product: Product): string {
  if (product.stockQuantity <= 0) return 'Out of stock'
  if (product.stockQuantity <= 5) return `Only ${product.stockQuantity} left in stock`
  return 'In Stock'
}

export function deriveDeliveryLabel(): string {
  return `Free delivery ${getStandardDeliveryLabel()}`
}
