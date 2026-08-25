import { createContext, useContext, useMemo, type ReactNode } from 'react'
import { useLocalStorage } from '../hooks/useLocalStorage'
import type { CartLine } from '../types/domain'
import type { Product } from '../services/api'

interface CartState {
  items: CartLine[]
  saved: CartLine[]
}

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

const EMPTY_STATE: CartState = { items: [], saved: [] }

export function CartProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useLocalStorage<CartState>('mercatto:cart', EMPTY_STATE)

  function addItem(product: Product, qty = 1) {
    setState((prev) => {
      const existing = prev.items.find((line) => line.productId === product.id)
      if (existing) {
        return {
          ...prev,
          items: prev.items.map((line) =>
            line.productId === product.id ? { ...line, qty: line.qty + qty } : line,
          ),
        }
      }
      return {
        ...prev,
        items: [...prev.items, { productId: product.id, name: product.name, price: product.price, qty }],
      }
    })
  }

  function incrementQty(productId: number) {
    setState((prev) => ({
      ...prev,
      items: prev.items.map((line) => (line.productId === productId ? { ...line, qty: line.qty + 1 } : line)),
    }))
  }

  function decrementQty(productId: number) {
    setState((prev) => ({
      ...prev,
      items: prev.items.map((line) =>
        line.productId === productId ? { ...line, qty: Math.max(1, line.qty - 1) } : line,
      ),
    }))
  }

  function removeItem(productId: number) {
    setState((prev) => ({ ...prev, items: prev.items.filter((line) => line.productId !== productId) }))
  }

  function saveForLater(productId: number) {
    setState((prev) => {
      const line = prev.items.find((l) => l.productId === productId)
      if (!line) return prev
      return {
        items: prev.items.filter((l) => l.productId !== productId),
        saved: [...prev.saved, line],
      }
    })
  }

  function moveToCart(productId: number) {
    setState((prev) => {
      const line = prev.saved.find((l) => l.productId === productId)
      if (!line) return prev
      const remainingSaved = prev.saved.filter((l) => l.productId !== productId)
      const existing = prev.items.find((l) => l.productId === productId)
      const items = existing
        ? prev.items.map((l) => (l.productId === productId ? { ...l, qty: l.qty + 1 } : l))
        : [...prev.items, { ...line, qty: line.qty + 1 }]
      return { items, saved: remainingSaved }
    })
  }

  function clear() {
    setState(EMPTY_STATE)
  }

  const itemCount = useMemo(() => state.items.reduce((sum, line) => sum + line.qty, 0), [state.items])
  const subtotal = useMemo(
    () => state.items.reduce((sum, line) => sum + line.price * line.qty, 0),
    [state.items],
  )

  return (
    <CartContext.Provider
      value={{
        items: state.items,
        saved: state.saved,
        itemCount,
        subtotal,
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

export function useCart(): CartContextValue {
  const context = useContext(CartContext)
  if (!context) throw new Error('useCart must be used within CartProvider')
  return context
}
