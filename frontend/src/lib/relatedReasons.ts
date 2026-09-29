import type { Product, RelatedReason } from '../services/api'
import { usd } from './format'

type Priced = Pick<Product, 'price'>
type Described = Pick<Product, 'price' | 'brand' | 'category'>

/**
 * Storefront copy for why `item` was recommended next to `current` (Card #223). Built only from
 * the reason the backend verified and the two products' own data, so it never claims something
 * the catalog can't back. Returns null — render nothing — for a reason this build doesn't know,
 * or when the data needed to phrase it is missing.
 */
export function relatedReasonLabel(reason: RelatedReason | string, current: Priced, item: Described): string | null {
  switch (reason) {
    case 'SAME_BRAND':
      return item.brand ? `More from ${item.brand}` : null
    case 'LOWER_PRICE': {
      const saving = current.price - item.price
      return saving > 0 ? `Similar item, ${usd(saving)} less` : null
    }
    case 'TOP_RATED_IN_CATEGORY':
      return `Highest rated in ${item.category}`
    case 'HIGHER_RATED':
      return 'Rated higher than this item'
    case 'SIMILAR_NAME':
      return 'Similar to this item'
    case 'SAME_CATEGORY':
      return `Also in ${item.category}`
    default:
      return null
  }
}
