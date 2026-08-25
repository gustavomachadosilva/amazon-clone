import { createContext, useContext, type ReactNode } from 'react'
import { useLocalStorage } from '../hooks/useLocalStorage'
import { BASE_REVIEWS } from '../lib/constants'
import type { Review } from '../types/domain'

interface ReviewsContextValue {
  getReviews: (productId: number) => Review[]
  addReview: (productId: number, review: Review) => void
  markHelpful: (productId: number, index: number) => void
}

const ReviewsContext = createContext<ReviewsContextValue | null>(null)

type ReviewsState = Record<number, Review[]>

export function ReviewsProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useLocalStorage<ReviewsState>('mercatto:reviews', {})

  function getReviews(productId: number): Review[] {
    return state[productId] ?? BASE_REVIEWS
  }

  function addReview(productId: number, review: Review) {
    setState((prev) => ({
      ...prev,
      [productId]: [review, ...(prev[productId] ?? [...BASE_REVIEWS])],
    }))
  }

  function markHelpful(productId: number, index: number) {
    setState((prev) => {
      const list = prev[productId] ?? [...BASE_REVIEWS]
      return {
        ...prev,
        [productId]: list.map((review, i) => (i === index ? { ...review, helpful: review.helpful + 1 } : review)),
      }
    })
  }

  return (
    <ReviewsContext.Provider value={{ getReviews, addReview, markHelpful }}>{children}</ReviewsContext.Provider>
  )
}

export function useReviews(): ReviewsContextValue {
  const context = useContext(ReviewsContext)
  if (!context) throw new Error('useReviews must be used within ReviewsProvider')
  return context
}
