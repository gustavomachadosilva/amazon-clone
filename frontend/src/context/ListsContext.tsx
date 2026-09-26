import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { useAuth } from '../context/AuthContext'
import { listsApi, type WishListView } from '../services/api'

export type ListsStatus = 'idle' | 'loading' | 'ready' | 'error'

interface ListsContextValue {
  lists: WishListView[]
  // idle = no signed-in user; loading = fetch in flight; ready = lists loaded; error = the last fetch failed.
  status: ListsStatus
  // Refetches the signed-in user's lists (e.g. "Try again" after an error). No-op when signed out.
  reload: () => void
  createList: (name: string) => Promise<WishListView>
  addToList: (listId: number, productId: number) => Promise<'added' | 'exists'>
  removeFromList: (listId: number, productId: number) => Promise<void>
  deleteList: (listId: number) => Promise<void>
}

const ListsContext = createContext<ListsContextValue | null>(null)

export function ListsProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [lists, setLists] = useState<WishListView[]>([])
  // Starts as 'loading' when someone is already signed in, so consumers don't flash an empty state
  // before the first fetch has even begun.
  const [status, setStatus] = useState<ListsStatus>(() => (user ? 'loading' : 'idle'))
  const [reloadTick, setReloadTick] = useState(0)
  // Mirrors `lists` synchronously (state updates are async), so a mutation that
  // resolves right after another can still compute its update from the latest
  // confirmed server data instead of a stale render snapshot.
  const listsRef = useRef(lists)
  // Identifies which user "owns" the current lists at any given time, so a
  // fetch/mutation that resolves after logout (or after a different user has
  // since logged in) can detect it's stale and skip updateLists instead of
  // overwriting the current lists with another session's data.
  const userIdRef = useRef<number | null>(null)
  // Increments per fetch, so an older in-flight response (e.g. the failed request a retry replaced)
  // can't overwrite the result of a newer one.
  const requestSeqRef = useRef(0)

  function updateLists(next: WishListView[]) {
    listsRef.current = next
    setLists(next)
  }

  useEffect(() => {
    userIdRef.current = user?.id ?? null
    const requestSeq = ++requestSeqRef.current
    if (!user) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- reseta listas ao deslogar; sincroniza com autenticação externa, fora do escopo deste card
      updateLists([])
      setStatus('idle')
      return
    }
    const ownerId = user.id
    setStatus('loading')
    const isCurrent = () => userIdRef.current === ownerId && requestSeqRef.current === requestSeq
    listsApi
      .listMine()
      .then((fetched) => {
        if (!isCurrent()) return
        updateLists(fetched)
        setStatus('ready')
      })
      .catch((err) => {
        console.error(err)
        if (isCurrent()) setStatus('error')
      })
  }, [user?.id, reloadTick]) // eslint-disable-line react-hooks/exhaustive-deps -- refetch apenas quando o id do usuário muda ou num reload explícito

  function reload() {
    if (!user) return
    setStatus('loading')
    setReloadTick((tick) => tick + 1)
  }

  async function createList(name: string): Promise<WishListView> {
    if (!user) throw new Error('createList requires an authenticated user')
    const ownerId = user.id
    const created = await listsApi.create(name)
    // Prepended: the server returns lists newest-first, so a fresh list belongs at the top.
    if (userIdRef.current === ownerId) updateLists([created, ...listsRef.current])
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
    <ListsContext.Provider value={{ lists, status, reload, createList, addToList, removeFromList, deleteList }}>
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
