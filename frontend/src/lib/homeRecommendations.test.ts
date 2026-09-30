import { describe, expect, it } from 'vitest'
import { homeSectionCopy } from './homeRecommendations'

describe('homeSectionCopy', () => {
  it('says "Recommended for you" only for a personalized shelf', () => {
    expect(homeSectionCopy('PERSONALIZED')).toEqual({ heading: 'Recommended for you', seeAllHref: '/search' })
  })

  it('labels the fallback "Top rated" and links to the catalogue sorted by rating', () => {
    const copy = homeSectionCopy('TOP_RATED')
    expect(copy).toEqual({ heading: 'Top rated', seeAllHref: '/search?sort=rating' })
    expect(copy.heading).not.toMatch(/for you/i)
  })
})
