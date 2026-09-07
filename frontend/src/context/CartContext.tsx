import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useAuth } from '../context/AuthContext'
import { cartApi, type CartItemView, type CartView, type Product } from '../services/api'
import type { CartLine } from '../types/domain'

interface CartContextValue {
  items: CartLine[]
  saved: CartLine[]
  itemCount: number
  subtotal: number
  addItem: (product: Product, qty?: number) => void
  incrementQty: (productId: number) => void
  decrementQty: (productId: number) => void
  removeItem: (productId: number) => void
  saveForLater: (productId: number) => void
  moveToCart: (productId: number) => void
  clear: () => void
}

const CartContext = createContext<CartContextValue | null>(null)

const EMPTY_VIEW: CartView = { userId: 0, items: [], savedForLater: [], itemCount: 0, total: 0 }

function toLine(item: CartItemView): CartLine {
  return { productId: item.productId, name: item.productName, price: item.unitPrice, qty: item.quantity }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [view, setView] = useState<CartView>(EMPTY_VIEW)
  // Mirrors `view` synchronously (state updates are async), and chains mutations
  // one after another so a second mutation always computes from the previous
  // one's confirmed server response instead of a stale render snapshot —
  // otherwise two quick decrements can both compute the same "next" value and
  // silently lose one of them.
  const viewRef = useRef(view)
  const queueRef = useRef<Promise<CartView>>(Promise.resolve(view))

  function updateView(next: CartView) {
    viewRef.current = next
    setView(next)
  }

  useEffect(() => {
    if (!user) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- reseta carrinho ao deslogar; sincroniza com autenticação externa, fora do escopo deste card
      updateView(EMPTY_VIEW)
      queueRef.current = Promise.resolve(EMPTY_VIEW)
      return
    }
    queueRef.current = cartApi
      .get(user.id)
      .then((fetched) => {
        updateView(fetched)
        return fetched
      })
      .catch((err) => {
        console.error(err)
        return EMPTY_VIEW
      })
  }, [user?.id]) // eslint-disable-line react-hooks/exhaustive-deps -- refetch apenas quando o id do usuário muda

  function applyMutation(mutate: (current: CartView) => Promise<CartView>) {
    queueRef.current = queueRef.current
      .then(mutate)
      .then((updated) => {
        updateView(updated)
        return updated
      })
      .catch((err) => {
        console.error(err)
        return viewRef.current
      })
  }

  function addItem(product: Product, qty = 1) {
    if (!user) return
    applyMutation(() => cartApi.addItem(user.id, product.id, qty))
  }

  function incrementQty(productId: number) {
    if (!user) return
    applyMutation(() => cartApi.addItem(user.id, productId, 1))
  }

  function decrementQty(productId: number) {
    if (!user) return
    applyMutation((current) => {
      const line = current.items.find((i) => i.productId === productId)
      if (!line) return Promise.resolve(current)
      const next = Math.max(1, line.quantity - 1)
      return cartApi.updateQuantity(user.id, productId, next)
    })
  }

  function removeItem(productId: number) {
    if (!user) return
    applyMutation(() => cartApi.removeItem(user.id, productId))
  }

  function saveForLater(productId: number) {
    if (!user) return
    applyMutation(() => cartApi.saveForLater(user.id, productId))
  }

  function moveToCart(productId: number) {
    if (!user) return
    applyMutation(() => cartApi.moveToCart(user.id, productId))
  }

  function clear() {
    if (!user) return
    applyMutation(() => cartApi.clear(user.id))
  }

  return (
    <CartContext.Provider
      value={{
        items: view.items.map(toLine),
        saved: view.savedForLater.map(toLine),
        itemCount: view.itemCount,
        subtotal: view.total,
        addItem,
        incrementQty,
        decrementQty,
        removeItem,
        saveForLater,
        moveToCart,
        clear,
      }}
    >
      {children}
    </CartContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components -- hook colocalizado com o Provider; separar em arquivo próprio é refatoração fora do escopo deste card
export function useCart(): CartContextValue {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart must be used within CartProvider')
  return context
}
