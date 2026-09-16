import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { SlidersHorizontal } from 'lucide-react'
import { Blueprint, Button, Pagination, Placeholder, Select, StarRating } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useReviews } from '../context/ReviewsContext'
import { catalogApi, type Page, type Product } from '../services/api'
import { useCategories } from '../hooks/useCategories'
import { usd } from '../lib/format'
import { installmentLine } from '../lib/pricing'
import {
  deriveDeliveryLabel,
  deriveFastDelivery,
  deriveListPrice,
  deriveStockLabel,
} from '../lib/mockProductMeta'
import { onEnterKey } from '../lib/a11y'

const RATING_OPTIONS = [4.5, 4, 3, 0]

export default function Search() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const { user } = useAuth()
  const cart = useCart()
  const reviews = useReviews()
  const categories = useCategories()

  const q = searchParams.get('q') ?? ''
  const category = searchParams.get('category') ?? 'All'
  const maxPrice = Number(searchParams.get('maxPrice') ?? 600)
  const minRating = Number(searchParams.get('minRating') ?? 0)
  const fastOnly = searchParams.get('fast') === '1'
  const sort = searchParams.get('sort') ?? 'relevance'
  const pageParam = Math.max(0, Number(searchParams.get('page') ?? 1) - 1)

  const requestKey = `${q}|${category}|${pageParam}`
  const [pageData, setPageData] = useState<Page<Product> | null>(null)
  const [retryTick, setRetryTick] = useState(0)
  const attemptKey = `${requestKey}#${retryTick}`
  const [resolvedKey, setResolvedKey] = useState<string | null>(null)
  const [resolvedOk, setResolvedOk] = useState(true)
  const loading = resolvedKey !== attemptKey
  const loadError = !loading && !resolvedOk

  useEffect(() => {
    let cancelled = false
    catalogApi
      .search(q || undefined, category === 'All' ? undefined : category, pageParam)
      .then((data) => {
        if (cancelled) return
        setPageData(data)
        setResolvedOk(true)
        setResolvedKey(attemptKey)
      })
      .catch(() => {
        if (cancelled) return
        setResolvedOk(false)
        setResolvedKey(attemptKey)
      })
    return () => {
      cancelled = true
    }
  }, [q, category, pageParam, attemptKey])

  function ratingOf(product: Product): number {
    const list = reviews.getReviews(product.id)
    return list.reduce((sum, r) => sum + r.stars, 0) / list.length
  }

  const [filtersOpen, setFiltersOpen] = useState(false)

  const results = pageData?.content ?? []
  const hasActiveFilters = maxPrice < 600 || minRating > 0 || fastOnly

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
    if (key !== 'page') {
      next.delete('page')
    }
    setSearchParams(next)
  }

  return (
    <div className="grid w-full grid-cols-1 gap-5 px-4 py-4 md:grid-cols-[236px_1fr] md:gap-7 md:px-8 md:py-6 lg:px-10">
      <button
        type="button"
        className="btn btn-secondary flex min-h-11 w-full items-center justify-between md:hidden"
        aria-expanded={filtersOpen}
        onClick={() => setFiltersOpen((open) => !open)}
      >
        <span>Filters</span>
        <SlidersHorizontal size={18} strokeWidth={1.5} />
      </button>

      <aside className={filtersOpen ? 'block' : 'hidden md:block'}>
        <h2 className="hidden text-[24px] md:block">Filters</h2>
        <div className="mt-3 md:mt-3">
          <div className="field-label mb-1.5">Department</div>
          {categories.map((name) => (
            <label className="radio mb-1.5 flex" key={name}>
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

        <div className="mt-5">
          <div className="field-label mb-1.5">Price up to</div>
          <input
            type="range"
            min={20}
            max={600}
            step={10}
            value={maxPrice}
            onChange={(e) => setParam('maxPrice', e.target.value)}
            className="w-full accent-accent"
          />
          <div className="text-[16px]">{usd(maxPrice)}</div>
        </div>

        <div className="mt-5">
          <div className="field-label mb-1.5">Customer rating</div>
          {RATING_OPTIONS.map((value) => (
            <label className="radio mb-1.5 flex" key={value}>
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

        <div className="mt-5">
          <label className="radio flex">
            <input type="checkbox" checked={fastOnly} onChange={(e) => setParam('fast', e.target.checked ? '1' : null)} />
            <span className="box" />
            Arrives tomorrow
          </label>
        </div>
      </aside>

      <section>
        <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div className="text-[16.5px] text-paper-700">
            {loading
              ? 'Loading…'
              : loadError
                ? 'Search unavailable'
                : hasActiveFilters
                  ? `${filtered.length} of ${results.length} results on this page match your filters`
                  : `${filtered.length} results`}{' '}
            {!loading && !loadError && q && `for "${q}"`}{' '}
            {!loading && !loadError && category !== 'All' && `in ${category}`}
          </div>
          <Select
            className="sm:w-auto"
            value={sort}
            onChange={(e) => setParam('sort', e.target.value === 'relevance' ? null : e.target.value)}
          >
            <option value="relevance">Relevance</option>
            <option value="low">Price: low to high</option>
            <option value="high">Price: high to low</option>
            <option value="rating">Avg. customer review</option>
          </Select>
        </div>

        {loading ? (
          <Blueprint className="p-8 text-center">
            <p className="text-paper-700">Loading…</p>
          </Blueprint>
        ) : loadError ? (
          <Blueprint className="p-8 text-center">
            <h3>Something went wrong</h3>
            <p className="text-paper-700">We couldn&apos;t load these results. Please try again.</p>
            <Button variant="secondary" onClick={() => setRetryTick((tick) => tick + 1)}>
              Retry
            </Button>
          </Blueprint>
        ) : filtered.length === 0 ? (
          <Blueprint className="p-8 text-center">
            <h3>No results</h3>
            <p className="text-paper-700">Try another keyword or clear the filters.</p>
            <Button variant="secondary" onClick={() => setSearchParams({})}>
              Clear filters
            </Button>
          </Blueprint>
        ) : (
          <div className="flex flex-col gap-4">
            {filtered.map((product) => (
              <Blueprint
                key={product.id}
                className="flex flex-col gap-4 p-4 md:grid md:grid-cols-[180px_1fr_210px]"
              >
                <div
                  className="max-w-[160px] cursor-pointer md:max-w-none"
                  role="link"
                  tabIndex={0}
                  onClick={() => navigate(`/product/${product.id}`)}
                  onKeyDown={onEnterKey(() => navigate(`/product/${product.id}`))}
                >
                  <Placeholder label={product.name} aspect="1/1" src={product.imageUrl} />
                </div>
                <div>
                  <div
                    className="h cursor-pointer text-xl"
                    role="link"
                    tabIndex={0}
                    onClick={() => navigate(`/product/${product.id}`)}
                    onKeyDown={onEnterKey(() => navigate(`/product/${product.id}`))}
                  >
                    {product.name}
                  </div>
                  <div className="text-[16px] text-paper-600">Sold by Seller #{product.sellerId}</div>
                  <div className="my-1 flex items-center gap-1.5 text-xs">
                    <StarRating rating={ratingOf(product)} />
                    <span>{ratingOf(product).toFixed(1)}</span>
                    <span className="text-paper-600">({reviews.getReviews(product.id).length})</span>
                  </div>
                  <p className="max-w-[52ch] text-[16.5px] text-paper-700">{product.description}</p>
                  <div className="flex flex-wrap gap-2">
                    <span className="tag tag-outline">{product.category}</span>
                    <span className="tag tag-accent">
                      {deriveFastDelivery(product) ? 'Fast delivery' : 'Free shipping'}
                    </span>
                  </div>
                </div>
                <div className="border-t border-divider pt-3 md:border-l md:border-t-0 md:pl-4 md:pt-0">
                  <div className="readout text-2xl font-semibold text-foreground">{usd(product.price)}</div>
                  {deriveListPrice(product) > product.price && (
                    <div className="readout text-xs text-paper-500 line-through">{usd(deriveListPrice(product))}</div>
                  )}
                  <div className="text-[15px] text-paper-700">{installmentLine(product.price)}</div>
                  <div className="text-[15px] text-paper-700">{deriveDeliveryLabel(product)}</div>
                  <div className="text-[16.5px] text-accent-700">{deriveStockLabel(product)}</div>
                  <Button
                    variant="primary"
                    block
                    onClick={() => {
                      if (!user) {
                        navigate('/signin')
                        return
                      }
                      cart.addItem(product)
                      navigate('/cart')
                    }}
                  >
                    Add to cart
                  </Button>
                  <Button variant="secondary" block onClick={() => navigate(`/product/${product.id}`)}>
                    View details
                  </Button>
                </div>
              </Blueprint>
            ))}
          </div>
        )}

        {!loadError && pageData && pageData.totalPages > 1 && (
          <Pagination
            currentPage={pageData.number}
            totalPages={pageData.totalPages}
            totalElements={hasActiveFilters ? undefined : pageData.totalElements}
            pageSize={pageData.size}
            onPageChange={(newPage) => setParam('page', (newPage + 1).toString())}
          />
        )}
      </section>
    </div>
  )
}
