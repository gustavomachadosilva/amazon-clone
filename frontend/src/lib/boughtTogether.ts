import type { BoughtTogether, BoughtTogetherItem, Product, RelatedProduct } from '../services/api'
import { relatedReasonLabel } from './relatedReasons'

type Current = Pick<Product, 'price'>

/**
 * Heading and caption for the product page bundle (Card #224). Only CO_PURCHASE bundles — items
 * other customers really bought together — may say "bought together"; the SIMILAR cold-start
 * fallback says plainly that it's a similarity suggestion.
 */
export function bundleCopy(source: BoughtTogether['source']): { heading: string; caption: string | null } {
  return source === 'CO_PURCHASE'
    ? { heading: 'Frequently bought together', caption: null }
    : {
        heading: 'Pairs well with this item',
        caption: 'Not enough purchase history yet — suggested by similarity to this item',
      }
}

/** Per-item note under a bundle item's name, or null to render nothing. */
export function bundleItemNote(item: BoughtTogetherItem, current: Current): string | null {
  if (item.timesBoughtTogether != null) {
    const n = item.timesBoughtTogether
    return `Bought together by ${n} ${n === 1 ? 'customer' : 'customers'}`
  }
  return item.primaryReason ? relatedReasonLabel(item.primaryReason, current, item.product) : null
}

/**
 * The related products minus the ones a SIMILAR bundle already shows, so the similarity-based
 * sections never recommend the same product twice on the page (Card #223's rule). A CO_PURCHASE
 * bundle makes a different claim (bought together, not similar), so it doesn't hide anything.
 */
export function withoutBundleItems(related: RelatedProduct[], bundle: BoughtTogether | null): RelatedProduct[] {
  if (!bundle || bundle.source !== 'SIMILAR') return related
  const shown = new Set(bundle.items.map((item) => item.product.id))
  return related.filter((item) => !shown.has(item.product.id))
}
