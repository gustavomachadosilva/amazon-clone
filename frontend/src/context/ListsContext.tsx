import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useAuth } from '../context/AuthContext'
import { listsApi, type WishListView } from '../services/api'

interface ListsContextValue {
  lists: WishListView[]
  createList: (name: string) => Promise<WishListView>
  addToList: (listId: number, productId: number) => Promise<'added' | 'exists'>
  removeFromList: (listId: number, productId: number) => Promise<void>
  deleteList: (listId: number) => Promise<void>
}

const ListsContext = createContext<ListsContextValue | null>(null)

export function ListsProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [lists, setLists] = useState<WishListView[]>([])
  // Mirrors `lists` synchronously (state updates are async), so a mutation that
  // resolves right after another can still compute its update from the latest
  // confirmed server data instead of a stale render snapshot.
  const listsRef = useRef(lists)
  // Identifies which user "owns" the current lists at any given time, so a
  // fetch/mutation that resolves after logout (or after a different user has
  // since logged in) can detect it's stale and skip updateLists instead of
  // overwriting the current lists with another session's data.
  const userIdRef = useRef<number | null>(null)

  function updateLists(next: WishListView[]) {
    listsRef.current = next
    setLists(next)
  }

  useEffect(() => {
    userIdRef.current = user?.id ?? null
    if (!user) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- reseta listas ao deslogar; sincroniza com autenticação externa, fora do escopo deste card
      updateLists([])
      return
    }
    const ownerId = user.id
    listsApi
      .listMine()
      .then((fetched) => {
        if (userIdRef.current === ownerId) updateLists(fetched)
      })
      .catch((err) => {
        console.error(err)
      })
  }, [user?.id]) // eslint-disable-line react-hooks/exhaustive-deps -- refetch apenas quando o id do usuário muda

  async function createList(name: string): Promise<WishListView> {
    if (!user) throw new Error('createList requires an authenticated user')
    const ownerId = user.id
    const created = await listsApi.create(name)
    if (userIdRef.current === ownerId) updateLists([...listsRef.current, created])
    return created
  }

  async function addToList(listId: number, productId: number): Promise<'added' | 'exists'> {
    if (!user) throw new Error('addToList requires an authenticated user')
    const ownerId = user.id
    const result = await listsApi.addItem(listId, productId)
    if (userIdRef.current === ownerId) {
      updateLists(listsRef.current.map((list) => (list.id === listId ? result.list : list)))
    }
    return result.alreadyPresent ? 'exists' : 'added'
  }

  async function removeFromList(listId: number, productId: number): Promise<void> {
    if (!user) return
    const ownerId = user.id
    const updated = await listsApi.removeItem(listId, productId)
    if (userIdRef.current === ownerId) {
      updateLists(listsRef.current.map((list) => (list.id === listId ? updated : list)))
    }
  }

  async function deleteList(listId: number): Promise<void> {
    if (!user) return
    const ownerId = user.id
    await listsApi.remove(listId)
    if (userIdRef.current === ownerId) {
      updateLists(listsRef.current.filter((list) => list.id !== listId))
    }
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
