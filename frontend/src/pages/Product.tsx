import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import Placeholder from '../components/ui/Placeholder'
import StarRating from '../components/ui/StarRating'
import ProductGridCard from '../components/ProductGridCard'
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

export default function Product() {
  const { id } = useParams<{ id: string }>()
  const productId = Number(id)
  const navigate = useNavigate()
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
    if (lists.lists.length > 0 && !listTarget) setListTarget(lists.lists[0].id)
  }, [lists.lists, listTarget])

  if (!product) return <div style={{ maxWidth: 1280, margin: '0 auto', padding: 24 }}>Loading…</div>

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
    setListFeedback(result === 'added' ? `Added to ${list?.name ?? 'list'}.` : `Already in ${list?.name ?? 'list'}.`)
  }

  function saveNewList() {
    if (!newListName.trim()) return
    const list = lists.createList(newListName)
    setListTarget(list.id)
    setNewListName('')
    setCreatingList(false)
    setListFeedback(`List "${list.name}" created.`)
  }

  return (
    <div style={{ maxWidth: 1280, margin: '0 auto', padding: 24 }}>
      <div style={{ fontSize: 12.5, color: '#7a7a7d', marginBottom: 16 }}>
        <span style={{ cursor: 'pointer' }} onClick={() => navigate('/')}>
          Home
        </span>{' '}
        /{' '}
        <span style={{ cursor: 'pointer' }} onClick={() => navigate(`/search?category=${product.category}`)}>
          {product.category}
        </span>{' '}
        / {product.name}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '420px 1fr 300px', gap: 28, alignItems: 'start' }}>
        <Blueprint style={{ padding: 12 }}>
          <Placeholder label="Main photo" aspect="1/1" src={product.imageUrl} />
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginTop: 8 }}>
            {['Angle 2', 'Angle 3', 'Detail', 'In use'].map((label) => (
              <Placeholder key={label} label={label} aspect="1/1" />
            ))}
          </div>
        </Blueprint>

        <div>
          <h1 style={{ fontSize: 34, lineHeight: 1.08 }}>{product.name}</h1>
          <div style={{ fontSize: 13, marginBottom: 8 }}>
            Brand: <a href="#brand">{deriveBrandLabel(product)}</a>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 14, paddingBottom: 12, borderBottom: '1px solid var(--color-divider)' }}>
            <StarRating rating={rating} />
            <span>{rating.toFixed(1)}</span>
            <span style={{ color: '#7a7a7d' }}>{productReviews.length.toLocaleString('en-US')} ratings</span>
          </div>

          <div style={{ margin: '16px 0' }}>
            <div className="h" style={{ fontSize: 38, color: 'var(--color-accent-800)' }}>
              {usd(product.price)}
            </div>
            {listPrice > product.price && (
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                <span style={{ fontSize: 14, color: '#98989b', textDecoration: 'line-through' }}>{usd(listPrice)}</span>
                <span className="tag tag-accent">-{discountPct}%</span>
              </div>
            )}
            <div style={{ fontSize: 12.5, color: '#5d5d60' }}>{installmentLine(product.price)}</div>
          </div>

          <div className="kick">About this item</div>
          <ul style={{ fontSize: 13.5, paddingLeft: 18 }}>
            {bullets.map((bullet) => (
              <li key={bullet}>{bullet}</li>
            ))}
          </ul>

          <Blueprint style={{ padding: 16, marginTop: 20 }}>
            <div className="h" style={{ fontSize: 16, marginBottom: 8 }}>
              Product details
            </div>
            <table className="table">
              <tbody>
                <tr>
                  <td>Brand</td>
                  <td>{deriveBrandLabel(product)}</td>
                </tr>
                <tr>
                  <td>Department</td>
                  <td>{product.category}</td>
                </tr>
                <tr>
                  <td>Item model number</td>
                  <td>{deriveModelNumber(product)}</td>
                </tr>
                <tr>
                  <td>Warranty</td>
                  <td>{WARRANTY_LABEL}</td>
                </tr>
                <tr>
                  <td>Sold by</td>
                  <td>{STORE_NAME}</td>
                </tr>
              </tbody>
            </table>
          </Blueprint>
        </div>

        <Blueprint as="aside" style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 10, position: 'sticky', top: 16 }}>
          <div className="h" style={{ fontSize: 28 }}>
            {usd(product.price)}
          </div>
          <div style={{ fontSize: 13 }}>{deriveDeliveryLabel(product)}</div>
          <div style={{ fontSize: 12.5, color: '#7a7a7d' }}>Ships from and sold by {STORE_NAME}</div>
          <div className="h" style={{ fontSize: 17, color: 'var(--color-accent-700)' }}>
            {deriveStockLabel(product)}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 13, color: '#5d5d60' }}>Qty</span>
            <select className="input" style={{ width: 'auto', minHeight: 32 }} value={qty} onChange={(e) => setQty(Number(e.target.value))}>
              {[1, 2, 3, 4, 5].map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </select>
          </div>
          <button
            className="btn btn-primary btn-block"
            onClick={() => {
              cart.addItem(product, qty)
              navigate('/cart')
            }}
          >
            Add to cart
          </button>
          <button
            className="btn btn-secondary btn-block"
            onClick={() => {
              cart.addItem(product, qty)
              navigate('/checkout')
            }}
          >
            Buy now
          </button>

          <div style={{ border: '1px solid var(--color-divider)', padding: 12, display: 'flex', flexDirection: 'column', gap: 8, marginTop: 4 }}>
            <div className="kick">Add to a list</div>
            {!creatingList ? (
              <>
                <select className="input" value={listTarget} onChange={(e) => setListTarget(e.target.value)}>
                  {lists.lists.map((list) => (
                    <option key={list.id} value={list.id}>
                      {list.name} ({list.items.length})
                    </option>
                  ))}
                </select>
                <button className="btn btn-secondary" onClick={addToList}>
                  Add to list
                </button>
                <button className="btn btn-ghost" onClick={() => setCreatingList(true)}>
                  + Create a new list
                </button>
              </>
            ) : (
              <>
                <input
                  className="input"
                  placeholder="New list name"
                  value={newListName}
                  onChange={(e) => setNewListName(e.target.value)}
                />
                <button className="btn btn-primary" onClick={saveNewList}>
                  Save
                </button>
              </>
            )}
            {listFeedback && <div style={{ fontSize: 12, color: 'var(--color-accent-700)' }}>{listFeedback}</div>}
          </div>

          <div className="hr" />
          <div style={{ fontSize: 12, color: '#5d5d60', lineHeight: 1.5 }}>
            Free returns within 30 days · Secure payment · 12-month warranty
          </div>
        </Blueprint>
      </div>

      {alsoViewed.length > 0 && (
        <div style={{ borderTop: '1px solid var(--color-divider)', paddingTop: 28, marginTop: 28 }}>
          <h2>Customers who viewed this item also viewed</h2>
          <p style={{ fontSize: 13, color: '#5d5d60' }}>Based on browsing sessions that included {product.name}</p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 16 }}>
            {alsoViewed.map((item, index) => (
              <div key={item.id}>
                <ProductGridCard product={item} compact />
                <div style={{ fontSize: 11, color: 'var(--color-accent-700)', marginTop: 4 }}>
                  {ALSO_VIEWED_SHARES[index] ?? 10}% also viewed this
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {(bundleItems.length > 1 || recommended.length > 0) && (
        <div style={{ borderTop: '1px solid var(--color-divider)', paddingTop: 28, marginTop: 28 }}>
          <h2>Recommended based on this item</h2>
          <p style={{ fontSize: 13, color: '#5d5d60' }}>
            Frequently bought with or instead of this {product.category} pick
          </p>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24 }}>
            <Blueprint style={{ padding: 16 }}>
              <div className="h" style={{ fontSize: 16, marginBottom: 8 }}>
                Frequently bought together
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 12 }}>
                {bundleItems.map((item, index) => (
                  <div key={item.id} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    {index > 0 && <span>+</span>}
                    <Placeholder label="Item" aspect="1/1" className="w-[84px]" src={item.imageUrl} />
                  </div>
                ))}
              </div>
              {bundleItems.map((item, index) => (
                <label key={item.id} className="radio" style={{ display: 'flex', marginBottom: 6 }}>
                  <input type="checkbox" checked={bundleChecked.has(item.id)} onChange={() => toggleBundle(item.id)} />
                  <span className="box" />
                  {index === 0 ? `This item: ${item.name}` : item.name}
                </label>
              ))}
              <div className="h" style={{ fontSize: 24, color: 'var(--color-accent-800)', marginTop: 8 }}>
                Total price: {usd(bundleTotal)}
              </div>
              <div style={{ fontSize: 12, color: '#7a7a7d', marginBottom: 8 }}>
                {bundleChecked.size} of {bundleItems.length} items selected
              </div>
              <button className="btn btn-primary" onClick={addBundleToCart}>
                Add selected to cart
              </button>
            </Blueprint>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {recommended.map((item, index) => (
                <div key={item.id} style={{ display: 'grid', gridTemplateColumns: '88px 1fr 150px', gap: 12 }}>
                  <div style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${item.id}`)}>
                    <Placeholder label="Item" aspect="1/1" src={item.imageUrl} />
                  </div>
                  <div>
                    <div style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${item.id}`)}>
                      {item.name}
                    </div>
                    <StarRating rating={rating} />
                    <div style={{ fontSize: 12, color: 'var(--color-accent-700)' }}>
                      {RELATED_REASONS[index % RELATED_REASONS.length].replace('{category}', product.category)}
                    </div>
                  </div>
                  <div>
                    <div className="h">{usd(item.price)}</div>
                    <button
                      className="btn btn-secondary"
                      onClick={() => {
                        cart.addItem(item)
                        navigate('/cart')
                      }}
                    >
                      Add to cart
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      <div style={{ borderTop: '1px solid var(--color-divider)', paddingTop: 28, marginTop: 28 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Customer reviews</h2>
          <button className="btn btn-secondary" onClick={() => navigate(`/product/${product.id}/review`)}>
            Write a review
          </button>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 24 }}>
          <div>
            <div className="h" style={{ fontSize: 40 }}>
              {rating.toFixed(1)}
            </div>
            <StarRating rating={rating} />
            <div style={{ fontSize: 12.5, color: '#7a7a7d', marginBottom: 12 }}>
              {productReviews.length.toLocaleString('en-US')} global ratings
            </div>
            {RATING_DISTRIBUTION.map((row) => (
              <div key={row.label} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                <span style={{ fontSize: 11, width: 24 }}>{row.label}</span>
                <div style={{ flex: 1, height: 9, border: '1px solid var(--color-divider)' }}>
                  <div style={{ width: `${row.pct}%`, height: '100%', background: 'var(--color-accent)' }} />
                </div>
                <span style={{ fontSize: 11, width: 30 }}>{row.pct}%</span>
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            {productReviews.map((review, index) => (
              <div key={`${review.author}-${index}`}>
                <StarRating rating={review.stars} />
                <div className="h" style={{ fontSize: 15 }}>
                  {review.title}
                </div>
                <div style={{ fontSize: 12.5, color: '#7a7a7d', marginBottom: 6 }}>
                  {review.author} · {review.date} · Verified purchase
                </div>
                <p style={{ fontSize: 13.5, maxWidth: '70ch', color: '#424244' }}>{review.text}</p>
                <button className="btn btn-ghost" onClick={() => reviews.markHelpful(product.id, index)}>
                  Helpful ({review.helpful})
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
