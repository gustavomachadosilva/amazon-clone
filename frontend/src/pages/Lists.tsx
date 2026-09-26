import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Blueprint, Button, Input, Placeholder, StarRating } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useLists } from '../context/ListsContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { usd } from '../lib/format'
import { deriveDeliveryLabel, deriveStockLabel } from '../lib/mockProductMeta'
import { onEnterKey, onEnterOrSpaceKey } from '../lib/a11y'
import type { WishListView } from '../services/api'

// `?list={id}` preselects a list (e.g. from the Your Account page); anything non-numeric is ignored.
function parseListParam(value: string | null): number | null {
  return value !== null && /^\d+$/.test(value) ? Number(value) : null
}

export default function Lists() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const lists = useLists()
  const cart = useCart()
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedId = parseListParam(searchParams.get('list'))
  // The requested list when it exists, otherwise the first one (unknown ids fall back silently).
  const pickDefault = (all: WishListView[]) =>
    all.find((l) => l.id === requestedId)?.id ?? all[0]?.id ?? null
  const [activeListId, setActiveListId] = useState<number | null>(() => pickDefault(lists.lists))
  const [newListName, setNewListName] = useState('')
  const [actionError, setActionError] = useState('')

  useEffect(() => {
    if (!lists.lists.find((l) => l.id === activeListId)) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- realinha seleção ativa quando a lista some; refatorar para estado derivado é fora do escopo deste card
      setActiveListId(pickDefault(lists.lists))
    }
  }, [lists.lists, activeListId, requestedId]) // eslint-disable-line react-hooks/exhaustive-deps -- pickDefault só depende de requestedId, já listado

  // Keeps `?list=` in step with the selection so the URL can be shared or reloaded.
  function selectList(id: number) {
    setActiveListId(id)
    setSearchParams({ list: String(id) }, { replace: true })
  }

  const activeList = lists.lists.find((l) => l.id === activeListId) ?? null
  const { products } = useProductsByIds(activeList?.productIds ?? [])

  async function createList() {
    if (!newListName.trim()) return
    try {
      const list = await lists.createList(newListName)
      selectList(list.id)
      setNewListName('')
    } catch {
      setActionError('Could not create the list. Please try again.')
    }
  }

  function deleteActiveList() {
    if (!activeList) return
    lists.deleteList(activeList.id).catch(() => {
      setActionError('Could not delete the list. Please try again.')
    })
  }

  function removeFromActiveList(productId: number) {
    if (!activeList) return
    lists.removeFromList(activeList.id, productId).catch(() => {
      setActionError('Could not remove the item. Please try again.')
    })
  }

  function addAllToCart() {
    if (!user) return
    if (!activeList) return
    activeList.productIds.forEach((productId) => {
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
                onClick={() => selectList(list.id)}
                onKeyDown={onEnterOrSpaceKey(() => selectList(list.id))}
                className={`flex flex-none cursor-pointer items-center gap-2 border border-divider px-3 py-2.5 ${active ? 'bg-surface' : 'bg-transparent'}`}
              >
                <span
                  className="h-2 w-2 flex-none rounded-full"
                  style={{ background: active ? 'var(--color-accent)' : 'transparent', boxShadow: active ? 'none' : 'inset 0 0 0 1.5px var(--color-divider)' }}
                  aria-hidden="true"
                />
                <div>
                  <div className={active ? 'font-medium text-accent-800' : ''}>{list.name}</div>
                  <div className="text-[15px] text-paper-600">{list.productIds.length} items</div>
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
                <p className="text-[16.5px] text-paper-700">{activeList.productIds.length} item(s) · private list</p>
              </div>
              <div className="flex gap-2">
                <Button variant="secondary" onClick={addAllToCart}>
                  Add all to cart
                </Button>
                <Button variant="ghost" onClick={deleteActiveList}>
                  Delete list
                </Button>
              </div>
            </div>

            {actionError && (
              <div role="alert" className="callout-alert mt-2">
                {actionError}
              </div>
            )}

            {activeList.productIds.length === 0 ? (
              <Blueprint className="mt-4 p-8 text-center">
                <h3>This list is empty</h3>
                <p className="text-paper-700">Open a product and use &ldquo;Add to list&rdquo; to save it here.</p>
                <Button variant="primary" onClick={() => navigate('/')}>
                  Browse products
                </Button>
              </Blueprint>
            ) : (
              <div className="mt-4 flex flex-col gap-4">
                {activeList.productIds.map((productId) => {
                  const product = products.get(productId)
                  if (!product) return null
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
                          <StarRating rating={product.averageRating} />
                          <span className="text-paper-600">({product.reviewCount})</span>
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
                        <Button
                          variant="ghost"
                          block
                          onClick={() => removeFromActiveList(productId)}
                        >
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
