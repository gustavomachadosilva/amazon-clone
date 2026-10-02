import type { HomeRecommendationLayer } from '../services/api'

/**
 * Heading and "See all" target for the Home's product shelf (Card #225). Only a PERSONALIZED shelf —
 * built from the user's own purchases, cart and lists — may say "Recommended for you"; the fallback
 * is honestly labelled "Top rated" and links to the catalogue sorted by rating.
 */
export function homeSectionCopy(layer: HomeRecommendationLayer): { heading: string; seeAllHref: string } {
  return layer === 'PERSONALIZED'
    ? { heading: 'Recommended for you', seeAllHref: '/search' }
    : { heading: 'Top rated', seeAllHref: '/search?sort=rating' }
}
