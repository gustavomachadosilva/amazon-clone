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
    <div className="grid w-full grid-cols-1 gap-6 px-4 py-4 md:grid-cols-[250px_1fr] md:gap-7 md:px-8 md:py-6 lg:px-10">
      <aside>
        <h2 className="text-[24px]">Your lists</h2>
        <div className="mt-3 flex gap-2 overflow-x-auto md:flex-col md:gap-1 md:overflow-visible">
          {lists.lists.map((list) => {
            const active = list.id === activeListId
            return (
              <div
                key={list.id}
                role="button"
                tabIndex={0}
                aria-pressed={active}
                onClick={() => setActiveListId(list.id)}
                onKeyDown={onEnterOrSpaceKey(() => setActiveListId(list.id))}
                className={`flex flex-none cursor-pointer items-center gap-2 border border-divider px-3 py-2.5 ${active ? 'bg-surface' : 'bg-transparent'}`}
              >
                <span
                  className="h-2 w-2 flex-none rounded-full"
                  style={{ background: active ? 'var(--color-accent)' : 'transparent', boxShadow: active ? 'none' : 'inset 0 0 0 1.5px var(--color-divider)' }}
                  aria-hidden="true"
                />
                <div>
                  <div className={active ? 'font-medium text-accent-800' : ''}>{list.name}</div>
                  <div className="text-[15px] text-paper-600">{list.items.length} items</div>
                </div>
              </div>
            )
          })}
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
            <p className="text-paper-700">Open a product and use &ldquo;Add to list&rdquo; to save it here.</p>
            <Button variant="primary" onClick={() => navigate('/')}>
              Browse products
            </Button>
          </Blueprint>
        ) : (
          <>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h1>{activeList.name}</h1>
                <p className="text-[16.5px] text-paper-700">{activeList.items.length} item(s) · private list</p>
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
                <p className="text-paper-700">Open a product and use &ldquo;Add to list&rdquo; to save it here.</p>
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
                          <span className="text-paper-600">({productReviews.length})</span>
                        </div>
                        <div className="text-xs text-paper-700">
                          {deriveDeliveryLabel(product)} · {deriveStockLabel(product)}
                        </div>
                      </div>
                      <div className="col-span-2 sm:col-span-1">
                        <div className="readout text-[26px] font-semibold">{usd(product.price)}</div>
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
