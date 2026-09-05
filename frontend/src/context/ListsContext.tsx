import { createContext, useContext, type ReactNode } from 'react'
import { useLocalStorage } from '../hooks/useLocalStorage'
import type { WishList } from '../types/domain'

interface ListsContextValue {
  lists: WishList[]
  createList: (name: string) => WishList
  addToList: (listId: string, productId: number) => 'added' | 'exists'
  removeFromList: (listId: string, productId: number) => void
  deleteList: (listId: string) => void
}

const ListsContext = createContext<ListsContextValue | null>(null)

const SEED_LISTS: WishList[] = [
  { id: 'shopping-list', name: 'Shopping List', items: [] },
  { id: 'workshop-wishlist', name: 'Workshop wishlist', items: [] },
]

function slugify(name: string): string {
  return `${name.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-')}-${Date.now()}`
}

export function ListsProvider({ children }: { children: ReactNode }) {
  const [lists, setLists] = useLocalStorage<WishList[]>('mercatto:lists', SEED_LISTS)

  function createList(name: string): WishList {
    const list: WishList = { id: slugify(name), name: name.trim(), items: [] }
    setLists((prev) => [...prev, list])
    return list
  }

  function addToList(listId: string, productId: number): 'added' | 'exists' {
    let result: 'added' | 'exists' = 'added'
    setLists((prev) =>
      prev.map((list) => {
        if (list.id !== listId) return list
        if (list.items.includes(productId)) {
          result = 'exists'
          return list
        }
        return { ...list, items: [...list.items, productId] }
      }),
    )
    return result
  }

  function removeFromList(listId: string, productId: number) {
    setLists((prev) =>
      prev.map((list) => (list.id === listId ? { ...list, items: list.items.filter((id) => id !== productId) } : list)),
    )
  }

  function deleteList(listId: string) {
    setLists((prev) => prev.filter((list) => list.id !== listId))
  }

  return (
    <ListsContext.Provider value={{ lists, createList, addToList, removeFromList, deleteList }}>
      {children}
    </ListsContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components -- hook colocalizado com o Provider; separar em arquivo próprio é refatoração fora do escopo deste card
export function useLists(): ListsContextValue {
  const context = useContext(ListsContext)
  if (!context) throw new Error('useLists must be used within ListsProvider')
  return context
}
