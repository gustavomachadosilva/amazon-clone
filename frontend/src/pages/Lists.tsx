import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Blueprint, Button, Input, Placeholder, StarRating } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useLists } from '../context/ListsContext'
import { useReviews } from '../context/ReviewsContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { usd } from '../lib/format'
import { deriveDeliveryLabel, deriveStockLabel } from '../lib/mockProductMeta'
import { onEnterKey, onEnterOrSpaceKey } from '../lib/a11y'

export default function Lists() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const lists = useLists()
  const cart = useCart()
  const reviews = useReviews()
  const [activeListId, setActiveListId] = useState<string>(lists.lists[0]?.id ?? '')
  const [newListName, setNewListName] = useState('')

  useEffect(() => {
    if (!lists.lists.find((l) => l.id === activeListId)) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- realinha seleção ativa quando a lista some; refatorar para estado derivado é fora do escopo deste card
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
    if (!user) return
    if (!activeList) return
    activeList.items.forEach((productId) => {
      const product = products.get(productId)
      if (product) cart.addItem(product)
    })
  }

  return (
    <div className="mx-auto grid max-w-[1280px] grid-cols-1 gap-6 px-4 py-4 md:grid-cols-[250px_1fr] md:gap-7 md:px-6 md:py-6">
      <aside>
        <div className="kick">Your lists</div>
        <div className="mt-3 flex gap-2 overflow-x-auto md:flex-col md:gap-1 md:overflow-visible">
          {lists.lists.map((list) => (
            <div
              key={list.id}
              role="button"
              tabIndex={0}
              aria-pressed={list.id === activeListId}
              onClick={() => setActiveListId(list.id)}
              onKeyDown={onEnterOrSpaceKey(() => setActiveListId(list.id))}
              className="flex-none cursor-pointer border-l-2 px-3 py-2.5"
              style={{
                borderLeftColor: list.id === activeListId ? 'var(--color-accent)' : 'transparent',
                background: list.id === activeListId ? 'var(--color-surface)' : 'transparent',
              }}
            >
              <div>{list.name}</div>
              <div className="text-[11.5px] text-[#7a7a7d]">{list.items.length} items</div>
            </div>
          ))}
        </div>
        <div className="mt-4 flex gap-2">
          <Input
            placeholder="New list name"
            value={newListName}
            onChange={(e) => setNewListName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && createList()}
          />
          <Button variant="primary" onClick={createList}>
            Add
          </Button>
        </div>
      </aside>

      <section>
        {!activeList ? (
          <Blueprint className="p-8 text-center">
            <h3>This list is empty</h3>
            <p className="text-[#5d5d60]">Open a product and use &ldquo;Add to list&rdquo; to save it here.</p>
            <Button variant="primary" onClick={() => navigate('/')}>
              Browse products
            </Button>
          </Blueprint>
        ) : (
          <>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h1>{activeList.name}</h1>
                <p className="text-[13px] text-[#5d5d60]">{activeList.items.length} item(s) · private list</p>
              </div>
              <div className="flex gap-2">
                <Button variant="secondary" onClick={addAllToCart}>
                  Add all to cart
                </Button>
                <Button variant="ghost" onClick={() => lists.deleteList(activeList.id)}>
                  Delete list
                </Button>
              </div>
            </div>

            {activeList.items.length === 0 ? (
              <Blueprint className="mt-4 p-8 text-center">
                <h3>This list is empty</h3>
                <p className="text-[#5d5d60]">Open a product and use &ldquo;Add to list&rdquo; to save it here.</p>
                <Button variant="primary" onClick={() => navigate('/')}>
                  Browse products
                </Button>
              </Blueprint>
            ) : (
              <div className="mt-4 flex flex-col gap-4">
                {activeList.items.map((productId) => {
                  const product = products.get(productId)
                  if (!product) return null
                  const productReviews = reviews.getReviews(product.id)
                  const rating = productReviews.reduce((sum, r) => sum + r.stars, 0) / productReviews.length
                  return (
                    <Blueprint
                      key={productId}
                      className="grid grid-cols-[90px_1fr] gap-3 p-3 sm:grid-cols-[110px_1fr_190px]"
                    >
                      <div
                        className="cursor-pointer"
                        role="link"
                        tabIndex={0}
                        onClick={() => navigate(`/product/${productId}`)}
                        onKeyDown={onEnterKey(() => navigate(`/product/${productId}`))}
                      >
                        <Placeholder label={product.name} aspect="1/1" src={product.imageUrl} />
                      </div>
                      <div>
                        <div
                          className="cursor-pointer"
                          role="link"
                          tabIndex={0}
                          onClick={() => navigate(`/product/${productId}`)}
                          onKeyDown={onEnterKey(() => navigate(`/product/${productId}`))}
                        >
                          {product.name}
                        </div>
                        <div className="flex items-center gap-1.5 text-xs">
                          <StarRating rating={rating} />
                          <span className="text-[#7a7a7d]">({productReviews.length})</span>
                        </div>
                        <div className="text-xs text-[#5d5d60]">
                          {deriveDeliveryLabel(product)} · {deriveStockLabel(product)}
                        </div>
                      </div>
                      <div className="col-span-2 sm:col-span-1">
                        <div className="h text-[21px]">{usd(product.price)}</div>
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
                        <Button variant="ghost" block onClick={() => lists.removeFromList(activeList.id, productId)}>
                          Remove from list
                        </Button>
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
