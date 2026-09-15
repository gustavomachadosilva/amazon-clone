import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Blueprint, Button, Input, Placeholder, Textarea } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useReviews } from '../context/ReviewsContext'
import { catalogApi, type Product } from '../services/api'
import { RATING_WORD } from '../lib/constants'
import { deriveBrandLabel } from '../lib/mockProductMeta'
import type { Review } from '../types/domain'

export default function WriteReview() {
  const { id } = useParams<{ id: string }>()
  const productId = Number(id)
  const navigate = useNavigate()
  const { user } = useAuth()
  const reviews = useReviews()

  const [product, setProduct] = useState<Product | null>(null)
  const [rating, setRating] = useState(0)
  const [title, setTitle] = useState('')
  const [text, setText] = useState('')

  useEffect(() => {
    catalogApi.getById(productId).then(setProduct)
  }, [productId])

  if (!product) return <div className="mx-auto max-w-[760px] px-4 py-4 md:px-6 md:py-6">Loading…</div>

  function submit() {
    if (rating === 0) return
    const review: Review = {
      stars: rating as Review['stars'],
      title,
      author: user?.name ?? 'Guest',
      date: new Date().toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }),
      text,
      helpful: 0,
    }
    reviews.addReview(productId, review)
    navigate(`/product/${productId}`)
  }

  return (
    <div className="mx-auto max-w-[760px] px-4 py-4 md:px-6 md:py-6">
      <h1>Create a review</h1>

      <Blueprint className="mb-4 flex items-center gap-4 p-4">
        <Placeholder label={product.name} aspect="1/1" className="w-[64px] flex-none sm:w-[82px]" src={product.imageUrl} />
        <div className="min-w-0">
          <div className="h truncate">{product.name}</div>
          <div className="text-[12.5px] text-[#7a7a7d]">
            {deriveBrandLabel(product)} · {product.category}
          </div>
        </div>
      </Blueprint>

      <Blueprint className="flex flex-col gap-4 p-5">
        <div>
          <div className="kick">Overall rating</div>
          <div className="my-2 flex gap-1">
            {[1, 2, 3, 4, 5].map((value) => (
              <button
                key={value}
                onClick={() => setRating(value)}
                className="flex h-11 w-11 items-center justify-center border-0 bg-transparent text-3xl leading-none"
                style={{ color: value <= rating ? 'var(--color-accent-700)' : '#b7b7ba' }}
                aria-label={`${value} star`}
              >
                ★
              </button>
            ))}
          </div>
          <div className="text-[13px] text-[#5d5d60]">{RATING_WORD[rating]}</div>
        </div>

        <Input
          label="What is most important to know?"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />

        <Textarea
          label="What did you like or dislike? What did you use this product for?"
          value={text}
          onChange={(e) => setText(e.target.value)}
        />

        <div className="ph h-24">
          <span>Add a photo or video</span>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row">
          <Button variant="primary" onClick={submit} disabled={rating === 0}>
            Submit review
          </Button>
          <Button variant="secondary" onClick={() => navigate(`/product/${productId}`)}>
            Cancel
          </Button>
        </div>
      </Blueprint>
    </div>
  )
}
