import { fullSizeImageUrl } from './productImage'

const BASE = 'https://m.media-amazon.com/images/I/'

describe('fullSizeImageUrl', () => {
  it('strips the seed thumbnail modifier', () => {
    expect(fullSizeImageUrl(`${BASE}81k57a1xhuL._AC_UL320_.jpg`)).toBe(`${BASE}81k57a1xhuL.jpg`)
  })

  it.each([
    ['81k57a1xhuL._AC_UY218_.jpg', '81k57a1xhuL.jpg'],
    ['81k57a1xhuL._AC_SX679_.jpg', '81k57a1xhuL.jpg'],
    ['61abcDEF12L._SL1500_.png', '61abcDEF12L.png'],
    ['81k57a1xhuL._AC_UL320_QL65_.jpg', '81k57a1xhuL.jpg'],
    ['816QYu6+DaL._AC_UL320_.jpg', '816QYu6+DaL.jpg'],
  ])('strips other modifiers and keeps the extension: %s', (file, expected) => {
    expect(fullSizeImageUrl(`${BASE}${file}`)).toBe(`${BASE}${expected}`)
  })

  it('keeps the query string and hash', () => {
    expect(fullSizeImageUrl(`${BASE}81k57a1xhuL._AC_UL320_.jpg?v=2#top`)).toBe(`${BASE}81k57a1xhuL.jpg?v=2#top`)
  })

  it('returns an Amazon URL without a modifier unchanged', () => {
    const url = `${BASE}81k57a1xhuL.jpg`
    expect(fullSizeImageUrl(url)).toBe(url)
  })

  it.each([
    'https://cdn.example.com/p/a._AC_UL320_.jpg',
    '/img/headphones.jpg',
    '',
    'https://m.media-amazon.com/images/G/01/logo._AC_UL320_.jpg',
  ])('returns other URLs unchanged: %j', (url) => {
    expect(fullSizeImageUrl(url)).toBe(url)
  })
})
