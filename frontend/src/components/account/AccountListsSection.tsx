import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Blueprint, Button, Input, Placeholder } from '../ui'
import { useLists } from '../../context/ListsContext'
import { useProductsByIds } from '../../hooks/useProductsByIds'
import type { Product, WishListView } from '../../services/api'

const MAX_LISTS = 4
const MAX_THUMBNAILS = 3

const GRID = 'grid grid-cols-[repeat(auto-fill,minmax(min(200px,100%),1fr))] gap-4'

function itemCountLabel(count: number): string {
  return `${count} ${count === 1 ? 'item' : 'items'}`
}

function ListsSkeleton() {
  return (
    <div role="status">
      <span className="sr-only">Loading your lists…</span>
      <div aria-hidden="true" className={`${GRID} animate-pulse`}>
        {[0, 1, 2].map((key) => (
          <div key={key} className="space-y-3 border border-divider p-4">
            <div className="grid grid-cols-3 gap-2">
              <div className="aspect-square rounded-ds-sm bg-paper-200" />
              <div className="aspect-square rounded-ds-sm bg-paper-200" />
              <div className="aspect-square rounded-ds-sm bg-paper-200" />
            </div>
            <div className="h-6 w-3/4 rounded-ds-sm bg-paper-200" />
            <div className="h-4 w-1/3 rounded-ds-sm bg-paper-200" />
          </div>
        ))}
      </div>
    </div>
  )
}

function CreateFirstList() {
  const { createList } = useLists()
  const [name, setName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed || submitting) return
    setSubmitting(true)
    setError('')
    try {
      // On success the provider adds the list, so this form unmounts in favour of the card grid.
      await createList(trimmed)
    } catch {
      setError('Could not create the list. Please try again.')
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <p className="text-[16px] text-paper-700">You haven't created any lists yet.</p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <Input
          aria-label="New list name"
          placeholder="e.g. Birthday ideas"
          maxLength={255}
          value={name}
          onChange={(event) => setName(event.target.value)}
          disabled={submitting}
          className="sm:max-w-sm"
        />
        <Button type="submit" variant="primary" className="mt-0" disabled={submitting}>
          Create list
        </Button>
      </form>
      {error && (
        <div role="alert" className="callout-alert">
          {error}
        </div>
      )}
    </div>
  )
}

interface ListCardProps {
  list: WishListView
  products: Map<number, Product>
  productsLoading: boolean
}

function ListCard({ list, products, productsLoading }: ListCardProps) {
  const count = list.productIds.length
  const thumbnailIds = list.productIds.slice(0, MAX_THUMBNAILS)

  return (
    <Link
      to={`/lists?list=${list.id}`}
      aria-label={`${list.name}, ${itemCountLabel(count)}`}
      className="block h-full focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent-600"
    >
      <Blueprint corners className="prod flex h-full flex-col gap-3 bg-card p-4">
        {thumbnailIds.length === 0 ? (
          <div className="flex aspect-[3/1] items-center justify-center border border-dashed border-divider text-[15px] text-paper-600">
            No items yet
          </div>
        ) : (
          <div className="grid grid-cols-3 gap-2">
            {thumbnailIds.map((productId) => {
              const product = products.get(productId)
              return product ? (
                <Placeholder key={productId} label={product.name} src={product.imageUrl} aspect="1/1" />
              ) : (
                // Neutral square while the product loads (or if it could not be fetched).
                <div
                  key={productId}
                  aria-hidden="true"
                  className={`aspect-square rounded-ds-sm bg-paper-200 ${productsLoading ? 'animate-pulse' : ''}`}
                />
              )
            })}
          </div>
        )}
        <div>
          <h3 className="card-title mb-0 break-words text-xl">{list.name}</h3>
          <p className="card-meta mt-1">{itemCountLabel(count)}</p>
        </div>
      </Blueprint>
    </Link>
  )
}

export default function AccountListsSection() {
  const { lists, status, reload } = useLists()

  const shown = lists.slice(0, MAX_LISTS)
  // One batched fetch for every thumbnail on screen, de-duplicated in a stable order.
  const thumbnailIds = [...new Set(shown.flatMap((list) => list.productIds.slice(0, MAX_THUMBNAILS)))]
  const { products, loading: productsLoading } = useProductsByIds(thumbnailIds)

  if (status === 'idle') return null

  const hasLists = status === 'ready' && lists.length > 0

  return (
    <Blueprint as="section" aria-labelledby="lists-heading" className="flex flex-col gap-4 bg-card p-4 md:p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="lists-heading" className="card-title mb-0">
          Your Lists
        </h2>
        {hasLists && (
          <Link to="/lists" className="text-accent-700 hover:underline">
            See all lists
          </Link>
        )}
      </div>

      {status === 'loading' && <ListsSkeleton />}

      {status === 'error' && (
        <div role="alert" className="callout-alert flex-col items-start">
          <span>We couldn't load your lists.</span>
          <Button variant="secondary" onClick={reload}>
            Try again
          </Button>
        </div>
      )}

      {status === 'ready' && lists.length === 0 && <CreateFirstList />}

      {hasLists && (
        <ul aria-label="Your lists" className={GRID}>
          {shown.map((list) => (
            <li key={list.id} className="h-full">
              <ListCard list={list} products={products} productsLoading={productsLoading} />
            </li>
          ))}
        </ul>
      )}
    </Blueprint>
  )
}
