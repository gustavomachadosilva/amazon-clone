import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import Placeholder from '../components/ui/Placeholder'
import StarRating from '../components/ui/StarRating'
import { useCart } from '../context/CartContext'
import { useLists } from '../context/ListsContext'
import { useReviews } from '../context/ReviewsContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { usd } from '../lib/format'
import { deriveDeliveryLabel, deriveStockLabel } from '../lib/mockProductMeta'

export default function Lists() {
  const navigate = useNavigate()
  const lists = useLists()
  const cart = useCart()
  const reviews = useReviews()
  const [activeListId, setActiveListId] = useState<string>(lists.lists[0]?.id ?? '')
  const [newListName, setNewListName] = useState('')

  useEffect(() => {
    if (!lists.lists.find((l) => l.id === activeListId)) {
      setActiveListId(lists.lists[0]?.id ?? '')
    }
  }, [lists.lists, activeListId])

  const activeList = lists.lists.find((l) => l.id === activeListId) ?? null
  const { products } = useProductsByIds(activeList?.items ?? [])

  function createList() {
    if (!newListName.trim()) return
    const list = lists.createList(newListName)
    setActiveListId(list.id)
    setNewListName('')
  }

  function addAllToCart() {
    if (!activeList) return
    activeList.items.forEach((productId) => {
      const product = products.get(productId)
      if (product) cart.addItem(product)
    })
  }

  return (
    <div style={{ maxWidth: 1280, margin: '0 auto', padding: 24, display: 'grid', gridTemplateColumns: '250px 1fr', gap: 28 }}>
      <aside>
        <div className="kick">Your lists</div>
        <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 4 }}>
          {lists.lists.map((list) => (
            <div
              key={list.id}
              onClick={() => setActiveListId(list.id)}
              style={{
                padding: '10px 12px',
                cursor: 'pointer',
                borderLeft: list.id === activeListId ? '2px solid var(--color-accent)' : '2px solid transparent',
                background: list.id === activeListId ? 'var(--color-surface)' : 'transparent',
              }}
            >
              <div>{list.name}</div>
              <div style={{ fontSize: 11.5, color: '#7a7a7d' }}>{list.items.length} items</div>
            </div>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
          <input
            className="input"
            placeholder="New list name"
            value={newListName}
            onChange={(e) => setNewListName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && createList()}
          />
          <button className="btn btn-primary" onClick={createList}>
            Add
          </button>
        </div>
      </aside>

      <section>
        {!activeList ? (
          <Blueprint style={{ padding: 34, textAlign: 'center' }}>
            <h3>This list is empty</h3>
            <p style={{ color: '#5d5d60' }}>Open a product and use &ldquo;Add to list&rdquo; to save it here.</p>
            <button className="btn btn-primary" onClick={() => navigate('/')}>
              Browse products
            </button>
          </Blueprint>
        ) : (
          <>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h1>{activeList.name}</h1>
                <p style={{ fontSize: 13, color: '#5d5d60' }}>{activeList.items.length} item(s) · private list</p>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button className="btn btn-secondary" onClick={addAllToCart}>
                  Add all to cart
                </button>
                <button className="btn btn-ghost" onClick={() => lists.deleteList(activeList.id)}>
                  Delete list
                </button>
              </div>
            </div>

            {activeList.items.length === 0 ? (
              <Blueprint style={{ padding: 34, textAlign: 'center', marginTop: 16 }}>
                <h3>This list is empty</h3>
                <p style={{ color: '#5d5d60' }}>Open a product and use &ldquo;Add to list&rdquo; to save it here.</p>
                <button className="btn btn-primary" onClick={() => navigate('/')}>
                  Browse products
                </button>
              </Blueprint>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16, marginTop: 16 }}>
                {activeList.items.map((productId) => {
                  const product = products.get(productId)
                  if (!product) return null
                  const productReviews = reviews.getReviews(product.id)
                  const rating = productReviews.reduce((sum, r) => sum + r.stars, 0) / productReviews.length
                  return (
                    <Blueprint key={productId} style={{ padding: 12, display: 'grid', gridTemplateColumns: '110px 1fr 190px', gap: 12 }}>
                      <div style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${productId}`)}>
                        <Placeholder label="Product" aspect="1/1" />
                      </div>
                      <div>
                        <div style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${productId}`)}>
                          {product.name}
                        </div>
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12 }}>
                          <StarRating rating={rating} />
                          <span style={{ color: '#7a7a7d' }}>({productReviews.length})</span>
                        </div>
                        <div style={{ fontSize: 12, color: '#5d5d60' }}>
                          {deriveDeliveryLabel(product)} · {deriveStockLabel(product)}
                        </div>
                      </div>
                      <div>
                        <div className="h" style={{ fontSize: 21 }}>
                          {usd(product.price)}
                        </div>
                        <button
                          className="btn btn-primary btn-block"
                          onClick={() => {
                            cart.addItem(product)
                            navigate('/cart')
                          }}
                        >
                          Add to cart
                        </button>
                        <button className="btn btn-ghost btn-block" onClick={() => lists.removeFromList(activeList.id, productId)}>
                          Remove from list
                        </button>
                      </div>
                    </Blueprint>
                  )
                })}
              </div>
            )}
          </>
        )}
      </section>
    </div>
  )
}
