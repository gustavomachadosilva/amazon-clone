import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Star } from 'lucide-react'
import { Blueprint, Button, Input, Placeholder, Textarea } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { ApiRequestError, catalogApi, reviewsApi, type Product } from '../services/api'
import { RATING_WORD } from '../lib/constants'
import MediaPicker from '../components/reviews/MediaPicker'

export default function WriteReview() {
  const { id } = useParams<{ id: string }>()
  const productId = Number(id)
  const navigate = useNavigate()
  const { user } = useAuth()

  const [product, setProduct] = useState<Product | null>(null)
  const [rating, setRating] = useState(0)
  const [title, setTitle] = useState('')
  const [text, setText] = useState('')
  const [files, setFiles] = useState<File[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    catalogApi.getById(productId).then(setProduct)
  }, [productId])

  useEffect(() => {
    // A review's authorId is now a real foreign key to a users row, so an anonymous "Guest"
    // submission is no longer possible — send unauthenticated visitors to sign in first,
    // mirroring the guard used for cart actions in Product.tsx.
    if (!user) navigate('/signin')
  }, [user, navigate])

  if (!product || !user) return <div className="mx-auto max-w-[860px] px-4 py-4 md:px-6 md:py-6">Loading…</div>

  async function submit() {
    if (rating === 0 || submitting) return
    setSubmitting(true)
    setError('')
    try {
      await reviewsApi.create(productId, { stars: rating, title, text }, files)
      navigate(`/product/${productId}`)
    } catch (e) {
      setError(e instanceof ApiRequestError && e.apiMessage ? e.apiMessage : 'Could not submit your review. Please try again.')
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-[860px] px-4 py-4 md:px-6 md:py-6">
      <h1>Create a review</h1>

      <Blueprint className="mb-4 flex items-center gap-4 p-4">
        <div className="w-[64px] flex-none sm:w-[82px]">
          <Placeholder label={product.name} aspect="1/1" src={product.imageUrl} />
        </div>
        <div className="min-w-0">
          <div className="h truncate">{product.name}</div>
          <div className="text-[16px] text-paper-600">
            {product.brand ? `${product.brand} · ` : ''}
            {product.category}
          </div>
        </div>
      </Blueprint>

      <Blueprint className="p-5" aria-busy={submitting}>
        <fieldset disabled={submitting} className="m-0 flex min-w-0 flex-col gap-4 border-0 p-0">
          <div>
            <h3 className="text-[22px]">Overall rating</h3>
            <div className="my-2 flex gap-1">
              {[1, 2, 3, 4, 5].map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={value === rating}
                  onClick={() => setRating(value)}
                  className="flex h-11 w-11 items-center justify-center border-0 bg-transparent text-accent-700"
                  aria-label={`${value} star`}
                >
                  <Star size={26} strokeWidth={1.5} fill={value <= rating ? 'currentColor' : 'none'} />
                </button>
              ))}
            </div>
            <div className="text-[16.5px] text-paper-700">{RATING_WORD[rating]}</div>
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

          <MediaPicker onChange={setFiles} disabled={submitting} />

          {error && (
            <div role="alert" className="text-sm text-accent-800">
              {error}
            </div>
          )}

          <div className="flex flex-col gap-3 sm:flex-row">
            <Button variant="primary" onClick={submit} disabled={rating === 0 || submitting}>
              {submitting ? (files.length > 0 ? 'Uploading…' : 'Submitting…') : 'Submit review'}
            </Button>
            <Button variant="secondary" onClick={() => navigate(`/product/${productId}`)}>
              Cancel
            </Button>
          </div>
        </fieldset>
      </Blueprint>
    </div>
  )
}
