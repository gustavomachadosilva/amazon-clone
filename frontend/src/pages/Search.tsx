import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { SlidersHorizontal } from 'lucide-react'
import { Blueprint, Button, Pagination, Select } from '../components/ui'
import ProductGridCard from '../components/ProductGridCard'
import { catalogApi, type Page, type Product, type ProductSort } from '../services/api'
import { useCategories } from '../hooks/useCategories'
import { usd } from '../lib/format'

const RATING_OPTIONS = [4.5, 4, 3, 0]
// The price slider's top stop means "no limit", so it is never sent to the API.
const MAX_PRICE_NO_LIMIT = 600
const SORTS: ProductSort[] = ['relevance', 'price_asc', 'price_desc', 'rating']
// Old links (e.g. a bookmarked "today's deals") used these values before sorting moved to the API.
const LEGACY_SORTS: Record<string, ProductSort> = { low: 'price_asc', high: 'price_desc' }

function parseSort(value: string | null): ProductSort {
  if (!value) return 'relevance'
  if (value in LEGACY_SORTS) return LEGACY_SORTS[value]
  return SORTS.includes(value as ProductSort) ? (value as ProductSort) : 'relevance'
}

export default function Search() {
  const [searchParams, setSearchParams] = useSearchParams()
  const categories = useCategories()

  const q = searchParams.get('q') ?? ''
  const category = searchParams.get('category') ?? 'All'
  const maxPrice = Number(searchParams.get('maxPrice') ?? MAX_PRICE_NO_LIMIT)
  const minRating = Number(searchParams.get('minRating') ?? 0)
  const sort = parseSort(searchParams.get('sort'))
  const pageParam = Math.max(0, Number(searchParams.get('page') ?? 1) - 1)

  const requestKey = `${q}|${category}|${maxPrice}|${minRating}|${sort}|${pageParam}`
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
      .search({
        query: q || undefined,
        category: category === 'All' ? undefined : category,
        maxPrice: maxPrice < MAX_PRICE_NO_LIMIT ? maxPrice : undefined,
        minRating: minRating > 0 ? minRating : undefined,
        sort,
        page: pageParam,
      })
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
  }, [q, category, maxPrice, minRating, sort, pageParam, attemptKey])

  const [filtersOpen, setFiltersOpen] = useState(false)

  const results = pageData?.content ?? []
  const totalResults = pageData?.totalElements ?? 0

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
            max={MAX_PRICE_NO_LIMIT}
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
      </aside>

      <section>
        <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div className="text-[16.5px] text-paper-700">
            {loading
              ? 'Loading…'
              : loadError
                ? 'Search unavailable'
                : `${totalResults} ${totalResults === 1 ? 'result' : 'results'}`}{' '}
            {!loading && !loadError && q && `for "${q}"`}{' '}
            {!loading && !loadError && category !== 'All' && `in ${category}`}
          </div>
          <Select
            className="sm:w-auto"
            value={sort}
            onChange={(e) => setParam('sort', e.target.value === 'relevance' ? null : e.target.value)}
          >
            <option value="relevance">Relevance</option>
            <option value="price_asc">Price: low to high</option>
            <option value="price_desc">Price: high to low</option>
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
        ) : results.length === 0 ? (
          <Blueprint className="p-8 text-center">
            <h3>No results</h3>
            <p className="text-paper-700">Try another keyword or clear the filters.</p>
            <Button variant="secondary" onClick={() => setSearchParams({})}>
              Clear filters
            </Button>
          </Blueprint>
        ) : (
          <div className="grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            {results.map((product) => (
              <ProductGridCard key={product.id} product={product} />
            ))}
          </div>
        )}

        {!loadError && pageData && pageData.totalPages > 1 && (
          <Pagination
            currentPage={pageData.number}
            totalPages={pageData.totalPages}
            totalElements={pageData.totalElements}
            pageSize={pageData.size}
            onPageChange={(newPage) => setParam('page', (newPage + 1).toString())}
          />
        )}
      </section>
    </div>
  )
}
