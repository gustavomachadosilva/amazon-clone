import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Blueprint, Button, Input, Placeholder, Select, StarRating, Table, TableBody, TableCell, TableRow } from '../components/ui'
import ProductGridCard from '../components/ProductGridCard'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useLists } from '../context/ListsContext'
import { useReviews } from '../context/ReviewsContext'
import { catalogApi, type Product as ProductType } from '../services/api'
import { usd } from '../lib/format'
import { installmentLine } from '../lib/pricing'
import { RATING_DISTRIBUTION, RELATED_REASONS, ALSO_VIEWED_SHARES, STORE_NAME } from '../lib/constants'
import {
  deriveBrandLabel,
  deriveDeliveryLabel,
  deriveDiscountPct,
  deriveListPrice,
  deriveModelNumber,
  deriveStockLabel,
  WARRANTY_LABEL,
} from '../lib/mockProductMeta'
import { onEnterKey } from '../lib/a11y'

export default function Product() {
  const { id } = useParams<{ id: string }>()
  const productId = Number(id)
  const navigate = useNavigate()
  const { user } = useAuth()
  const cart = useCart()
  const lists = useLists()
  const reviews = useReviews()

  const [product, setProduct] = useState<ProductType | null>(null)
  const [related, setRelated] = useState<ProductType[]>([])
  const [qty, setQty] = useState(1)
  const [listTarget, setListTarget] = useState<string>('')
  const [creatingList, setCreatingList] = useState(false)
  const [newListName, setNewListName] = useState('')
  const [listFeedback, setListFeedback] = useState('')
  const [bundleChecked, setBundleChecked] = useState<Set<number>>(new Set())

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
    if (lists.lists.length > 0 && !listTarget) setListTarget(lists.lists[0].id)
  }, [lists.lists, listTarget])

  if (!product) return <div className="mx-auto max-w-[1280px] px-4 py-4 md:px-6 md:py-6">Loading…</div>

  const productReviews = reviews.getReviews(product.id)
  const rating = productReviews.reduce((sum, r) => sum + r.stars, 0) / productReviews.length
  const listPrice = deriveListPrice(product)
  const discountPct = deriveDiscountPct(product)
  const bullets = [
    product.description,
    '12-month manufacturer warranty included.',
    `Compatible with the main accessories in the ${deriveBrandLabel(product)} line.`,
    'Ships in recyclable, single-box packaging.',
  ]

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

  function addToList() {
    if (!product) return
    let target = listTarget
    if (lists.lists.length === 0) {
      const created = lists.createList('Shopping List')
      target = created.id
      setListTarget(created.id)
    }
    const list = lists.lists.find((l) => l.id === target)
    const result = lists.addToList(target, product.id)
    setListFeedback(result ?? `Saved to ${list?.name ?? 'your list'}`)
    setTimeout(() => setListFeedback(''), 3000)
  }

  function saveNewList() {
    if (!newListName.trim() || !product) return
    const created = lists.createList(newListName.trim())
    lists.addToList(created.id, product.id)
    setListTarget(created.id)
    setCreatingList(false)
    setNewListName('')
    setListFeedback(`Saved to ${created.name}`)
    setTimeout(() => setListFeedback(''), 3000)
  }

  return (
    <div className="mx-auto max-w-[1280px] px-4 py-4 md:px-6 md:py-6">
      <div className="mb-4 text-[12.5px] text-[#7a7a7d]">
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
          onClick={() => navigate(`/search?category=${product.category}`)}
          onKeyDown={onEnterKey(() => navigate(`/search?category=${product.category}`))}
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
          <div className="kick">{product.category}</div>
          <h1 className="text-[28px] leading-[1.15] md:text-[34px]">{product.name}</h1>
          <div className="mb-2 text-[13px] text-accent-700">Visit the {deriveBrandLabel(product)} store</div>

          <div className="mb-3 flex items-center gap-2">
            <StarRating rating={rating} />
            <span className="text-accent-700">
              {rating.toFixed(1)} ({productReviews.length.toLocaleString('en-US')} ratings)
            </span>
          </div>

          <div className="hr" />

          <div className="my-3 flex flex-wrap items-baseline gap-2.5">
            {discountPct > 0 && (
              <span className="h text-2xl text-accent-800">-{discountPct}%</span>
            )}
            <span className="h text-4xl">{usd(product.price)}</span>
            {listPrice > product.price && (
              <span className="text-sm text-[#98989b] line-through">Typical price: {usd(listPrice)}</span>
            )}
          </div>
          <div className="text-[12.5px] text-[#5d5d60]">{installmentLine(product.price)}</div>

          <div className="hr" />

          <ul className="pl-[18px] text-[13.5px] leading-relaxed text-[#424244]">
            {bullets.map((b, i) => (
              <li key={i}>{b}</li>
            ))}
          </ul>

          <Blueprint className="mt-5 overflow-x-auto p-3.5">
            <div className="kick">Technical specifications</div>
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell className="w-[140px]">Brand</TableCell>
                  <TableCell>{deriveBrandLabel(product)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Model</TableCell>
                  <TableCell>{deriveModelNumber(product)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Category</TableCell>
                  <TableCell>{product.category}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Warranty</TableCell>
                  <TableCell>{WARRANTY_LABEL}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell>Sold by</TableCell>
                  <TableCell>{STORE_NAME}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Blueprint>
        </div>

        <Blueprint as="aside" className="flex flex-col gap-2.5 p-4 md:col-span-2 lg:sticky lg:top-4 lg:col-span-1 lg:p-[18px]">
          <div className="h text-[28px]">{usd(product.price)}</div>
          <div className="text-[13px]">{deriveDeliveryLabel(product)}</div>
          <div className="text-[12.5px] text-[#7a7a7d]">Ships from and sold by {STORE_NAME}</div>
          <div className="h text-[17px] text-accent-700">{deriveStockLabel(product)}</div>
          <div className="flex items-center gap-2">
            <span className="text-[13px] text-[#5d5d60]">Qty</span>
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
            <div className="kick">Add to a list</div>
            {!creatingList ? (
              <>
                <Select value={listTarget} onChange={(e) => setListTarget(e.target.value)}>
                  {lists.lists.map((list) => (
                    <option key={list.id} value={list.id}>
                      {list.name} ({list.items.length})
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
          <div className="text-xs leading-relaxed text-[#5d5d60]">
            Free returns within 30 days · Secure payment · 12-month warranty
          </div>
        </Blueprint>
      </div>

      {alsoViewed.length > 0 && (
        <div className="mt-7 border-t border-divider pt-7">
          <h2>Customers who viewed this item also viewed</h2>
          <p className="text-[13px] text-[#5d5d60]">Based on browsing sessions that included {product.name}</p>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-6">
            {alsoViewed.map((item, index) => (
              <div key={item.id}>
                <ProductGridCard product={item} compact />
                <div className="mt-1 text-[11px] text-accent-700">
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
          <p className="text-[13px] text-[#5d5d60]">
            Frequently bought with or instead of this {product.category} pick
          </p>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <Blueprint className="p-4">
              <div className="h mb-2 text-base">Frequently bought together</div>
              <div className="mb-3 flex flex-wrap items-center gap-1">
                {bundleItems.map((item, index) => (
                  <div key={item.id} className="flex items-center gap-1">
                    {index > 0 && <span>+</span>}
                    <Placeholder label={item.name} aspect="1/1" className="w-[84px]" src={item.imageUrl} />
                  </div>
                ))}
              </div>
              {bundleItems.map((item, index) => (
                <label key={item.id} className="radio mb-1.5 flex">
                  <input type="checkbox" checked={bundleChecked.has(item.id)} onChange={() => toggleBundle(item.id)} />
                  <span className="box" />
                  {index === 0 ? `This item: ${item.name}` : item.name}
                </label>
              ))}
              <div className="h mt-2 text-2xl text-accent-800">Total price: {usd(bundleTotal)}</div>
              <div className="mb-2 text-xs text-[#7a7a7d]">
                {bundleChecked.size} of {bundleItems.length} items selected
              </div>
              <Button variant="primary" onClick={addBundleToCart}>
                Add selected to cart
              </Button>
            </Blueprint>

            <div className="flex flex-col gap-3">
              {recommended.map((item, index) => (
                <div key={item.id} className="grid grid-cols-[72px_1fr] gap-3 sm:grid-cols-[88px_1fr_150px]">
                  <div
                    className="cursor-pointer"
                    role="link"
                    tabIndex={0}
                    onClick={() => navigate(`/product/${item.id}`)}
                    onKeyDown={onEnterKey(() => navigate(`/product/${item.id}`))}
                  >
                    <Placeholder label={item.name} aspect="1/1" src={item.imageUrl} />
                  </div>
                  <div>
                    <div
                      className="cursor-pointer"
                      role="link"
                      tabIndex={0}
                      onClick={() => navigate(`/product/${item.id}`)}
                      onKeyDown={onEnterKey(() => navigate(`/product/${item.id}`))}
                    >
                      {item.name}
                    </div>
                    <StarRating rating={rating} />
                    <div className="text-xs text-accent-700">
                      {RELATED_REASONS[index % RELATED_REASONS.length].replace('{category}', product.category)}
                    </div>
                  </div>
                  <div className="col-span-2 sm:col-span-1">
                    <div className="h">{usd(item.price)}</div>
                    <Button
                      variant="secondary"
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
              ))}
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
            <div className="h text-[40px]">{rating.toFixed(1)}</div>
            <StarRating rating={rating} />
            <div className="mb-3 text-[12.5px] text-[#7a7a7d]">
              {productReviews.length.toLocaleString('en-US')} global ratings
            </div>
            {RATING_DISTRIBUTION.map((row) => (
              <div key={row.label} className="mb-1 flex items-center gap-2">
                <span className="w-6 text-[11px]">{row.label}</span>
                <div className="h-[9px] flex-1 border border-divider">
                  <div className="h-full bg-accent" style={{ width: `${row.pct}%` }} />
                </div>
                <span className="w-[30px] text-[11px]">{row.pct}%</span>
              </div>
            ))}
          </div>
          <div className="flex flex-col gap-5">
            {productReviews.map((review, index) => (
              <div key={`${review.author}-${index}`}>
                <StarRating rating={review.stars} />
                <div className="h text-[15px]">{review.title}</div>
                <div className="mb-1.5 text-[12.5px] text-[#7a7a7d]">
                  {review.author} · {review.date} · Verified purchase
                </div>
                <p className="max-w-[70ch] text-[13.5px] text-[#424244]">{review.text}</p>
                <Button variant="ghost" onClick={() => reviews.markHelpful(product.id, index)}>
                  Helpful ({review.helpful})
                </Button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
