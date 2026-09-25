import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Blueprint, Button, Input, Placeholder, Select, StarRating, Table, TableBody, TableCell, TableRow } from '../components/ui'
import ProductGridCard from '../components/ProductGridCard'
import ReviewMediaThumbnails from '../components/reviews/ReviewMediaThumbnails'
import ReviewsWithImages from '../components/reviews/ReviewsWithImages'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useLists } from '../context/ListsContext'
import { catalogApi, reviewsApi, type Product as ProductType, type ReviewView } from '../services/api'
import { usd } from '../lib/format'
import { installmentLine } from '../lib/pricing'
import { RATING_DISTRIBUTION, RELATED_REASONS, ALSO_VIEWED_SHARES, STORE_NAME } from '../lib/constants'
import { deriveDeliveryLabel, deriveStockLabel } from '../lib/mockProductMeta'
import { onEnterKey } from '../lib/a11y'

export default function Product() {
  const { id } = useParams<{ id: string }>()
  const productId = Number(id)
  const navigate = useNavigate()
  const { user } = useAuth()
  const cart = useCart()
  const lists = useLists()

  const [product, setProduct] = useState<ProductType | null>(null)
  const [related, setRelated] = useState<ProductType[]>([])
  const [qty, setQty] = useState(1)
  const [listTarget, setListTarget] = useState<number | null>(null)
  const [creatingList, setCreatingList] = useState(false)
  const [newListName, setNewListName] = useState('')
  const [listFeedback, setListFeedback] = useState('')
  const [helpfulError, setHelpfulError] = useState('')
  const [bundleChecked, setBundleChecked] = useState<Set<number>>(new Set())

  // Independent from the product-loading state above: a failed reviews fetch must not block
  // the rest of the page (price, add to cart, specs, etc.) from rendering. Mirrors the
  // request-key/resolved-key pattern used for the results fetch in Search.tsx, so loading/error
  // are derived rather than set synchronously inside the effect body.
  const [productReviews, setProductReviews] = useState<ReviewView[]>([])
  const [reviewsRetryTick, setReviewsRetryTick] = useState(0)
  const reviewsRequestKey = product ? `${product.id}#${reviewsRetryTick}` : null
  const [resolvedReviewsKey, setResolvedReviewsKey] = useState<string | null>(null)
  const [reviewsResolvedOk, setReviewsResolvedOk] = useState(true)
  const reviewsLoading = reviewsRequestKey !== null && resolvedReviewsKey !== reviewsRequestKey
  const reviewsLoadError = !reviewsLoading && !reviewsResolvedOk

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- reseta quantidade ao trocar de produto; refatorar é fora do escopo deste card
    setQty(1)
    catalogApi.getById(productId).then(setProduct)
  }, [productId])

  useEffect(() => {
    if (!product) return
    catalogApi.search(undefined, product.category).then((page) => {
      const others = page.content.filter((p) => p.id !== product.id)
      setRelated(others)
      setBundleChecked(new Set([product.id, ...others.slice(0, 2).map((p) => p.id)]))
    })
  }, [product])

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- inicializa lista alvo default; refatorar para estado derivado é fora do escopo deste card
    if (lists.lists.length > 0 && listTarget === null) setListTarget(lists.lists[0].id)
  }, [lists.lists, listTarget])

  useEffect(() => {
    if (!product || !reviewsRequestKey) return
    let cancelled = false
    const key = reviewsRequestKey
    reviewsApi
      .listByProduct(product.id)
      .then((data) => {
        if (cancelled) return
        setProductReviews(data)
        setReviewsResolvedOk(true)
        setResolvedReviewsKey(key)
      })
      .catch(() => {
        if (cancelled) return
        setReviewsResolvedOk(false)
        setResolvedReviewsKey(key)
      })
    return () => {
      cancelled = true
    }
  }, [product, reviewsRequestKey])

  if (!product) return <div className="w-full px-4 py-4 md:px-8 md:py-6 lg:px-10">Loading…</div>

  const rating = product.averageRating
  const hasDiscount = product.listPrice !== null && product.listPrice > product.price
  const discountPct = hasDiscount
    ? Math.round((1 - product.price / (product.listPrice as number)) * 100)
    : 0
  const bullets = [
    product.description,
    product.warrantyMonths != null ? `${product.warrantyMonths}-month manufacturer warranty included.` : null,
    product.brand ? `Compatible with the main accessories in the ${product.brand} line.` : null,
    'Ships in recyclable, single-box packaging.',
  ].filter((bullet): bullet is string => Boolean(bullet))

  const alsoViewed = related.slice(0, 6)
  const recommended = related.slice(0, 4)
  const bundleItems = [product, ...related.slice(0, 2)]
  const bundleTotal = bundleItems.filter((p) => bundleChecked.has(p.id)).reduce((sum, p) => sum + p.price, 0)

  function toggleBundle(pid: number) {
    setBundleChecked((prev) => {
      const next = new Set(prev)
      if (next.has(pid)) next.delete(pid)
      else next.add(pid)
      return next
    })
  }

  function addBundleToCart() {
    if (!user) {
      navigate('/signin')
      return
    }
    bundleItems.filter((p) => bundleChecked.has(p.id)).forEach((p) => cart.addItem(p))
    navigate('/cart')
  }

  async function addToList() {
    if (!product) return
    if (!user) {
      navigate('/signin')
      return
    }
    try {
      let target = listTarget
      if (target === null) {
        const created = await lists.createList('Shopping List')
        target = created.id
        setListTarget(created.id)
      }
      const list = lists.lists.find((l) => l.id === target)
      const result = await lists.addToList(target, product.id)
      setListFeedback(result === 'exists' ? 'Already in this list' : `Saved to ${list?.name ?? 'your list'}`)
    } catch {
      setListFeedback('Could not save to list. Please try again.')
    }
    setTimeout(() => setListFeedback(''), 3000)
  }

  function markReviewHelpful(reviewId: number) {
    if (!user) {
      navigate('/signin')
      return
    }
    reviewsApi
      .markHelpful(reviewId)
      .then((updated) => {
        setProductReviews((prev) => prev.map((r) => (r.id === updated.id ? updated : r)))
      })
      .catch(() => {
        setHelpfulError('Could not mark review as helpful. Please try again.')
        setTimeout(() => setHelpfulError(''), 3000)
      })
  }

  async function saveNewList() {
    if (!newListName.trim() || !product) return
    if (!user) {
      navigate('/signin')
      return
    }
    try {
      const created = await lists.createList(newListName.trim())
      await lists.addToList(created.id, product.id)
      setListTarget(created.id)
      setCreatingList(false)
      setNewListName('')
      setListFeedback(`Saved to ${created.name}`)
    } catch {
      setListFeedback('Could not save to list. Please try again.')
    }
    setTimeout(() => setListFeedback(''), 3000)
  }

  return (
    <div className="w-full px-4 py-4 md:px-8 md:py-6 lg:px-10">
      <div className="mb-4 text-[16px] text-paper-600">
        <span
          className="cursor-pointer"
          role="link"
          tabIndex={0}
          onClick={() => navigate('/')}
          onKeyDown={onEnterKey(() => navigate('/'))}
        >
          Home
        </span>{' '}
        /{' '}
        <span
          className="cursor-pointer"
          role="link"
          tabIndex={0}
          onClick={() => navigate(`/search?category=${encodeURIComponent(product.category)}`)}
          onKeyDown={onEnterKey(() => navigate(`/search?category=${encodeURIComponent(product.category)}`))}
        >
          {product.category}
        </span>{' '}
        / {product.name}
      </div>

      <div className="grid grid-cols-1 items-start gap-6 md:grid-cols-[minmax(0,420px)_1fr] md:gap-7 lg:grid-cols-[420px_1fr_300px]">
        <Blueprint className="p-3">
          <Placeholder label="Main photo" aspect="1/1" src={product.imageUrl} priority />
          <div className="mt-2 grid grid-cols-4 gap-2">
            {['Angle 2', 'Angle 3', 'Detail', 'In use'].map((label) => (
              <Placeholder key={label} label={label} aspect="1/1" />
            ))}
          </div>
        </Blueprint>

        <div>
          <h1 className="text-[38px] leading-[1.15] md:text-[48px]">{product.name}</h1>
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <span className="tag tag-accent-2">{product.category}</span>
            {product.brand && (
              <span className="text-[16.5px] text-accent-700">Visit the {product.brand} store</span>
            )}
          </div>

          <div className="mb-3 flex items-center gap-2">
            <StarRating rating={rating} />
            <span className="text-accent-700">
              {rating.toFixed(1)} ({product.reviewCount.toLocaleString('en-US')} ratings)
            </span>
          </div>

          <div className="hr" />

          <div className="my-3 flex flex-wrap items-baseline gap-2.5">
            {hasDiscount && discountPct > 0 && (
              <span className="h text-2xl text-accent-800">-{discountPct}%</span>
            )}
            <span className="readout text-4xl font-semibold">{usd(product.price)}</span>
            {hasDiscount && (
              <span className="text-sm text-paper-500 line-through">Typical price: {usd(product.listPrice as number)}</span>
            )}
          </div>
          <div className="text-[16px] text-paper-700">{installmentLine(product.price)}</div>

          <div className="hr" />

          <ul className="pl-[18px] text-[17px] leading-relaxed text-paper-800">
            {bullets.map((b, i) => (
              <li key={i}>{b}</li>
            ))}
          </ul>

          <Blueprint className="mt-5 overflow-x-auto p-3.5">
            <h3 className="text-[22px]">Technical specifications</h3>
            <Table>
              <TableBody>
                {product.brand && (
                  <TableRow>
                    <TableCell className="w-[140px]">Brand</TableCell>
                    <TableCell>{product.brand}</TableCell>
                  </TableRow>
                )}
                {product.modelNumber && (
                  <TableRow>
                    <TableCell className="w-[140px]">Model</TableCell>
                    <TableCell>{product.modelNumber}</TableCell>
                  </TableRow>
                )}
                <TableRow>
                  <TableCell className="w-[140px]">Category</TableCell>
                  <TableCell>{product.category}</TableCell>
                </TableRow>
                {product.warrantyMonths != null && (
                  <TableRow>
                    <TableCell className="w-[140px]">Warranty</TableCell>
                    <TableCell>{product.warrantyMonths}-month limited warranty</TableCell>
                  </TableRow>
                )}
                <TableRow>
                  <TableCell className="w-[140px]">Sold by</TableCell>
                  <TableCell>{STORE_NAME}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Blueprint>
        </div>

        <Blueprint as="aside" className="flex flex-col gap-2.5 p-4 md:col-span-2 lg:sticky lg:top-4 lg:col-span-1 lg:p-[18px]">
          <div className="readout text-[38px] font-semibold">{usd(product.price)}</div>
          <div className="text-[16.5px]">{deriveDeliveryLabel(product)}</div>
          <div className="text-[16px] text-paper-600">Ships from and sold by {STORE_NAME}</div>
          <div className="h text-[22px] text-accent-700">{deriveStockLabel(product)}</div>
          <div className="flex items-center gap-2">
            <span className="text-[16.5px] text-paper-700">Qty</span>
            <Select className="w-auto min-h-8" value={qty} onChange={(e) => setQty(Number(e.target.value))}>
              {[1, 2, 3, 4, 5].map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </Select>
          </div>
          <Button
            variant="primary"
            block
            onClick={() => {
              if (!user) {
                navigate('/signin')
                return
              }
              cart.addItem(product, qty)
              navigate('/cart')
            }}
          >
            Add to cart
          </Button>
          <Button
            variant="secondary"
            block
            onClick={() => {
              if (!user) {
                navigate('/signin')
                return
              }
              cart.addItem(product, qty)
              navigate('/checkout')
            }}
          >
            Buy now
          </Button>

          <div className="mt-1 flex flex-col gap-2 border border-divider p-3">
            <h3 className="text-[18px]">Add to a list</h3>
            {!creatingList ? (
              <>
                <Select
                  value={listTarget !== null ? String(listTarget) : ''}
                  onChange={(e) => setListTarget(Number(e.target.value))}
                >
                  {lists.lists.map((list) => (
                    <option key={list.id} value={list.id}>
                      {list.name} ({list.productIds.length})
                    </option>
                  ))}
                </Select>
                <Button variant="secondary" onClick={addToList}>
                  Add to list
                </Button>
                <Button variant="ghost" onClick={() => setCreatingList(true)}>
                  + Create a new list
                </Button>
              </>
            ) : (
              <>
                <Input
                  placeholder="New list name"
                  value={newListName}
                  onChange={(e) => setNewListName(e.target.value)}
                />
                <Button variant="primary" onClick={saveNewList}>
                  Save
                </Button>
              </>
            )}
            {listFeedback && <div className="text-xs text-accent-700">{listFeedback}</div>}
          </div>

          <div className="hr" />
          <div className="text-xs leading-relaxed text-paper-700">
            Free returns within 30 days · Secure payment
            {product.warrantyMonths != null ? ` · ${product.warrantyMonths}-month warranty` : ''}
          </div>
        </Blueprint>
      </div>

      {alsoViewed.length > 0 && (
        <div className="mt-7 border-t border-divider pt-7">
          <h2>Customers who viewed this item also viewed</h2>
          <p className="text-[16.5px] text-paper-700">Based on browsing sessions that included {product.name}</p>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-6">
            {alsoViewed.map((item, index) => (
              <div key={item.id}>
                <ProductGridCard product={item} compact />
                <div className="mt-1 text-[14.5px] text-accent-700">
                  {ALSO_VIEWED_SHARES[index] ?? 10}% also viewed this
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {(bundleItems.length > 1 || recommended.length > 0) && (
        <div className="mt-7 border-t border-divider pt-7">
          <h2>Recommended based on this item</h2>
          <p className="text-[16.5px] text-paper-700">
            Frequently bought with or instead of this {product.category} pick
          </p>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <Blueprint className="p-4">
              <div className="h mb-3 text-base">Frequently bought together</div>

              <div className="mb-4 flex flex-wrap items-start justify-center gap-x-2 gap-y-3 sm:gap-x-3">
                {bundleItems.map((item, index) => (
                  <div key={item.id} className="flex items-center gap-2 sm:gap-3">
                    {index > 0 && (
                      <span className="h text-2xl leading-none text-paper-500" aria-hidden="true">
                        +
                      </span>
                    )}
                    <div
                      className="w-[76px] cursor-pointer text-center sm:w-[88px]"
                      role="link"
                      tabIndex={0}
                      onClick={() => navigate(`/product/${item.id}`)}
                      onKeyDown={onEnterKey(() => navigate(`/product/${item.id}`))}
                    >
                      <Placeholder label={item.name} aspect="1/1" src={item.imageUrl} />
                      <div className="readout mt-1 text-[13px] text-paper-700">{usd(item.price)}</div>
                    </div>
                  </div>
                ))}
              </div>

              <div className="hr" />

              <div className="mb-3 flex flex-col gap-2.5">
                {bundleItems.map((item, index) => (
                  <label key={item.id} className="radio flex w-full items-center gap-2.5">
                    <input type="checkbox" checked={bundleChecked.has(item.id)} onChange={() => toggleBundle(item.id)} />
                    <span className="box" />
                    <div className="h-9 w-9 shrink-0">
                      <Placeholder label={item.name} aspect="1/1" src={item.imageUrl} />
                    </div>
                    <span className="min-w-0 flex-1 overflow-hidden">
                      {index === 0 && (
                        <span className="block font-mono text-[10.5px] uppercase tracking-[.09em] text-paper-500">
                          This item
                        </span>
                      )}
                      <span className="block truncate text-sm" title={item.name}>
                        {item.name}
                      </span>
                    </span>
                    <span className="readout shrink-0 text-sm font-semibold">{usd(item.price)}</span>
                  </label>
                ))}
              </div>

              <div className="readout mt-2 text-2xl font-semibold text-accent-800">Total price: {usd(bundleTotal)}</div>
              <div className="mb-2 text-xs text-paper-600">
                {bundleChecked.size} of {bundleItems.length} items selected
              </div>
              <Button variant="primary" onClick={addBundleToCart}>
                Add selected to cart
              </Button>
            </Blueprint>

            <div className="flex flex-col">
              {recommended.map((item, index) => {
                return (
                  <div
                    key={item.id}
                    className="grid grid-cols-[64px_1fr] items-start gap-3 border-b border-divider py-3 first:pt-0 last:border-b-0 sm:grid-cols-[64px_1fr_auto]"
                  >
                    <div
                      className="cursor-pointer"
                      role="link"
                      tabIndex={0}
                      onClick={() => navigate(`/product/${item.id}`)}
                      onKeyDown={onEnterKey(() => navigate(`/product/${item.id}`))}
                    >
                      <Placeholder label={item.name} aspect="1/1" src={item.imageUrl} />
                    </div>
                    <div className="min-w-0">
                      <div
                        className="line-clamp-2 min-h-[40px] cursor-pointer text-[15px] leading-[1.35]"
                        role="link"
                        tabIndex={0}
                        title={item.name}
                        onClick={() => navigate(`/product/${item.id}`)}
                        onKeyDown={onEnterKey(() => navigate(`/product/${item.id}`))}
                      >
                        {item.name}
                      </div>
                      {item.reviewCount > 0 && (
                        <div className="mt-1 flex items-center gap-1.5">
                          <StarRating rating={item.averageRating} size={14} />
                          <span className="readout text-xs text-paper-600">{item.reviewCount}</span>
                        </div>
                      )}
                      <div className="mt-1 truncate text-xs text-accent-700">
                        {RELATED_REASONS[index % RELATED_REASONS.length].replace('{category}', product.category)}
                      </div>
                    </div>
                    <div className="col-span-2 flex items-center justify-between gap-3 sm:col-span-1 sm:flex-col sm:items-end sm:justify-start sm:gap-1.5">
                      <div className="readout text-lg font-semibold">{usd(item.price)}</div>
                      <Button
                        variant="secondary"
                        className="whitespace-nowrap"
                        onClick={() => {
                          if (!user) {
                            navigate('/signin')
                            return
                          }
                          cart.addItem(item)
                          navigate('/cart')
                        }}
                      >
                        Add to cart
                      </Button>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </div>
      )}

      <div className="mt-7 border-t border-divider pt-7">
        <div className="flex items-center justify-between">
          <h2>Customer reviews</h2>
          <Button variant="secondary" onClick={() => navigate(`/product/${product.id}/review`)}>
            Write a review
          </Button>
        </div>
        <div className="grid grid-cols-1 gap-6 md:grid-cols-[260px_1fr]">
          <div>
            <div className="h text-[54px]">{rating.toFixed(1)}</div>
            <StarRating rating={rating} />
            <div className="mb-3 text-[16px] text-paper-600">
              {product.reviewCount.toLocaleString('en-US')} global ratings
            </div>
            {RATING_DISTRIBUTION.map((row) => (
              <div key={row.label} className="mb-1 flex items-center gap-2">
                <span className="w-6 text-[14.5px]">{row.label}</span>
                <div className="h-[9px] flex-1 border border-divider">
                  <div className="h-full bg-accent" style={{ width: `${row.pct}%` }} />
                </div>
                <span className="w-[30px] text-[14.5px]">{row.pct}%</span>
              </div>
            ))}
          </div>
          {reviewsLoading ? (
            <div className="text-[16.5px] text-paper-700">Loading reviews…</div>
          ) : reviewsLoadError ? (
            <Blueprint className="p-6 text-center">
              <p className="text-paper-700">We couldn&apos;t load the reviews for this product.</p>
              <Button variant="secondary" onClick={() => setReviewsRetryTick((tick) => tick + 1)}>
                Retry
              </Button>
            </Blueprint>
          ) : productReviews.length === 0 ? (
            <div className="text-[16.5px] text-paper-700">No reviews yet. Be the first to write one.</div>
          ) : (
            <div className="flex flex-col gap-5">
              {helpfulError && <div className="text-xs text-accent-700">{helpfulError}</div>}
              <ReviewsWithImages reviews={productReviews} />
              {productReviews.map((review) => (
                <div key={review.id}>
                  <StarRating rating={review.stars} />
                  <div className="h text-[18px]">{review.title}</div>
                  <div className="mb-1.5 text-[16px] text-paper-600">
                    {review.authorName} ·{' '}
                    {new Date(review.createdAt).toLocaleDateString('en-US', {
                      month: 'long',
                      day: 'numeric',
                      year: 'numeric',
                    })}{' '}
                    · Verified purchase
                  </div>
                  <p className="max-w-[70ch] text-[17px] text-paper-800">{review.text}</p>
                  {review.media.length > 0 && (
                    <ReviewMediaThumbnails media={review.media} authorName={review.authorName} />
                  )}
                  <Button variant="ghost" onClick={() => markReviewHelpful(review.id)}>
                    Helpful ({review.helpfulCount})
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
