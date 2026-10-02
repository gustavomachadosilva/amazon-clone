import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Placeholder } from './ui'
import { fullSizeImageUrl } from '../lib/productImage'
import { catalogApi, type Product } from '../services/api'

const COLLAGE_SIZE = 3
// Fetched with headroom: products without a photo are skipped.
const CANDIDATES = 12

// Home hero image: a "sample shipment" of top-rated catalog products with photos — one large tile
// and two small ones, each linking to its product. Until (or unless) enough photos are found it
// keeps the striped placeholder from the design, so the hero never collapses or shows a half grid.
export default function HeroCollage() {
  const [products, setProducts] = useState<Product[] | null>(null)

  useEffect(() => {
    let cancelled = false
    catalogApi
      .search({ sort: 'rating', size: CANDIDATES })
      .then((page) => {
        if (!cancelled) setProducts(page.content.filter((product) => product.imageUrl).slice(0, COLLAGE_SIZE))
      })
      .catch(() => {
        if (!cancelled) setProducts([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  if (!products || products.length < COLLAGE_SIZE) {
    return <Placeholder label="Sample shipment" aspect="16/10" />
  }

  return (
    <div className="grid aspect-square grid-cols-[3fr_2fr] grid-rows-2 gap-2 md:aspect-[16/10] md:gap-3">
      {products.map((product, index) => (
        <Link
          key={product.id}
          to={`/product/${product.id}`}
          aria-label={`View ${product.name}`}
          className={`prod group relative flex min-h-0 flex-col overflow-hidden border border-divider bg-card ${
            index === 0 ? 'row-span-2' : ''
          }`}
        >
          <span className="truncate bg-surface px-3 py-1.5 font-mono text-[13px] uppercase tracking-wide text-paper-600">
            {product.category}
          </span>
          <img
            // Hero tiles are far larger than the catalog's ~320px thumbnails.
            src={fullSizeImageUrl(product.imageUrl!)}
            alt=""
            loading="eager"
            decoding="async"
            className="min-h-0 w-full flex-1 object-contain p-2 md:p-4"
          />
        </Link>
      ))}
    </div>
  )
}
