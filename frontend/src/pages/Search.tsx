import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import Placeholder from '../components/ui/Placeholder'
import StarRating from '../components/ui/StarRating'
import { useCart } from '../context/CartContext'
import { useReviews } from '../context/ReviewsContext'
import { catalogApi, type Product } from '../services/api'
import { CATEGORIES } from '../lib/constants'
import { usd } from '../lib/format'
import { installmentLine } from '../lib/pricing'
import {
  deriveDeliveryLabel,
  deriveFastDelivery,
  deriveListPrice,
  deriveStockLabel,
} from '../lib/mockProductMeta'

const RATING_OPTIONS = [4.5, 4, 3, 0]

export default function Search() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const cart = useCart()
  const reviews = useReviews()

  const q = searchParams.get('q') ?? ''
  const category = searchParams.get('category') ?? 'All'
  const maxPrice = Number(searchParams.get('maxPrice') ?? 600)
  const minRating = Number(searchParams.get('minRating') ?? 0)
  const fastOnly = searchParams.get('fast') === '1'
  const sort = searchParams.get('sort') ?? 'relevance'

  const [results, setResults] = useState<Product[]>([])

  useEffect(() => {
    catalogApi.search(q || undefined, category === 'All' ? undefined : category).then((page) => setResults(page.content))
  }, [q, category])

  function ratingOf(product: Product): number {
    const list = reviews.getReviews(product.id)
    return list.reduce((sum, r) => sum + r.stars, 0) / list.length
  }

  let filtered = results.filter((product) => {
    if (product.price > maxPrice) return false
    if (minRating > 0 && ratingOf(product) < minRating) return false
    if (fastOnly && !deriveFastDelivery(product)) return false
    return true
  })

  if (sort === 'low') filtered = [...filtered].sort((a, b) => a.price - b.price)
  else if (sort === 'high') filtered = [...filtered].sort((a, b) => b.price - a.price)
  else if (sort === 'rating') filtered = [...filtered].sort((a, b) => ratingOf(b) - ratingOf(a))

  function setParam(key: string, value: string | null) {
    const next = new URLSearchParams(searchParams)
    if (value === null) next.delete(key)
    else next.set(key, value)
    setSearchParams(next)
  }

  return (
    <div style={{ maxWidth: 1280, margin: '0 auto', padding: '24px', display: 'grid', gridTemplateColumns: '236px 1fr', gap: 28 }}>
      <aside>
        <div className="kick">Filters</div>
        <div style={{ marginTop: 12 }}>
          <div style={{ fontSize: 12, color: '#5d5d60', marginBottom: 6 }}>Department</div>
          {CATEGORIES.map((name) => (
            <label className="radio" key={name} style={{ display: 'flex', marginBottom: 6 }}>
              <input
                type="radio"
                name="category"
                checked={category === name}
                onChange={() => setParam('category', name === 'All' ? null : name)}
              />
              <span className="dot" />
              {name}
            </label>
          ))}
        </div>

        <div style={{ marginTop: 20 }}>
          <div style={{ fontSize: 12, color: '#5d5d60', marginBottom: 6 }}>Price up to</div>
          <input
            type="range"
            min={20}
            max={600}
            step={10}
            value={maxPrice}
            onChange={(e) => setParam('maxPrice', e.target.value)}
            style={{ width: '100%', accentColor: 'var(--color-accent)' }}
          />
          <div style={{ fontSize: 12.5 }}>{usd(maxPrice)}</div>
        </div>

        <div style={{ marginTop: 20 }}>
          <div style={{ fontSize: 12, color: '#5d5d60', marginBottom: 6 }}>Customer rating</div>
          {RATING_OPTIONS.map((value) => (
            <label className="radio" key={value} style={{ display: 'flex', marginBottom: 6 }}>
              <input
                type="radio"
                name="rating"
                checked={minRating === value}
                onChange={() => setParam('minRating', value ? String(value) : null)}
              />
              <span className="dot" />
              {value > 0 ? `${value} & up` : 'all ratings'}
            </label>
          ))}
        </div>

        <div style={{ marginTop: 20 }}>
          <label className="radio" style={{ display: 'flex' }}>
            <input type="checkbox" checked={fastOnly} onChange={(e) => setParam('fast', e.target.checked ? '1' : null)} />
            <span className="box" />
            Arrives tomorrow
          </label>
        </div>
      </aside>

      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div style={{ fontSize: 13, color: '#5d5d60' }}>
            {filtered.length} results {q && `for "${q}"`} {category !== 'All' && `in ${category}`}
          </div>
          <select
            className="input"
            style={{ width: 'auto' }}
            value={sort}
            onChange={(e) => setParam('sort', e.target.value === 'relevance' ? null : e.target.value)}
          >
            <option value="relevance">Relevance</option>
            <option value="low">Price: low to high</option>
            <option value="high">Price: high to low</option>
            <option value="rating">Avg. customer review</option>
          </select>
        </div>

        {filtered.length === 0 ? (
          <Blueprint style={{ padding: 34, textAlign: 'center' }}>
            <h3>No results</h3>
            <p style={{ color: '#5d5d60' }}>Try another keyword or clear the filters.</p>
            <button className="btn btn-secondary" onClick={() => setSearchParams({})}>
              Clear filters
            </button>
          </Blueprint>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {filtered.map((product) => (
              <Blueprint
                key={product.id}
                style={{ padding: 16, display: 'grid', gridTemplateColumns: '180px 1fr 210px', gap: 16 }}
              >
                <div style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${product.id}`)}>
                  <Placeholder label="Product" aspect="1/1" />
                </div>
                <div>
                  <div
                    className="h"
                    style={{ fontSize: 20, cursor: 'pointer' }}
                    onClick={() => navigate(`/product/${product.id}`)}
                  >
                    {product.name}
                  </div>
                  <div style={{ fontSize: 12.5, color: '#7a7a7d' }}>Sold by Seller #{product.sellerId}</div>
                  <div style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12, margin: '4px 0' }}>
                    <StarRating rating={ratingOf(product)} />
                    <span>{ratingOf(product).toFixed(1)}</span>
                    <span style={{ color: '#7a7a7d' }}>({reviews.getReviews(product.id).length})</span>
                  </div>
                  <p style={{ fontSize: 13, color: '#5d5d60', maxWidth: '52ch' }}>{product.description}</p>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <span className="tag tag-outline">{product.category}</span>
                    <span className="tag tag-accent">
                      {deriveFastDelivery(product) ? 'Fast delivery' : 'Free shipping'}
                    </span>
                  </div>
                </div>
                <div style={{ borderLeft: '1px solid var(--color-divider)', paddingLeft: 16 }}>
                  <div className="h" style={{ fontSize: 26 }}>
                    {usd(product.price)}
                  </div>
                  {deriveListPrice(product) > product.price && (
                    <div style={{ fontSize: 12, color: '#98989b', textDecoration: 'line-through' }}>
                      {usd(deriveListPrice(product))}
                    </div>
                  )}
                  <div style={{ fontSize: 11.5, color: '#5d5d60' }}>{installmentLine(product.price)}</div>
                  <div style={{ fontSize: 11.5, color: '#5d5d60' }}>{deriveDeliveryLabel(product)}</div>
                  <div style={{ fontSize: 13, color: 'var(--color-accent-700)' }}>{deriveStockLabel(product)}</div>
                  <button
                    className="btn btn-primary btn-block"
                    onClick={() => {
                      cart.addItem(product)
                      navigate('/cart')
                    }}
                  >
                    Add to cart
                  </button>
                  <button className="btn btn-secondary btn-block" onClick={() => navigate(`/product/${product.id}`)}>
                    View details
                  </button>
                </div>
              </Blueprint>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
