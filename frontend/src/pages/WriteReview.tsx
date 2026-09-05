import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import Placeholder from '../components/ui/Placeholder'
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

  if (!product) return <div style={{ maxWidth: 760, margin: '0 auto', padding: 24 }}>Loading…</div>

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
    <div style={{ maxWidth: 760, margin: '0 auto', padding: 24 }}>
      <h1>Create a review</h1>

      <Blueprint style={{ padding: 16, display: 'flex', alignItems: 'center', gap: 16, marginBottom: 16 }}>
        <Placeholder label="Product" aspect="1/1" className="w-[82px]" src={product.imageUrl} />
        <div>
          <div className="h">{product.name}</div>
          <div style={{ fontSize: 12.5, color: '#7a7a7d' }}>
            {deriveBrandLabel(product)} · {product.category}
          </div>
        </div>
      </Blueprint>

      <Blueprint style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div>
          <div className="kick">Overall rating</div>
          <div style={{ display: 'flex', gap: 4, margin: '8px 0' }}>
            {[1, 2, 3, 4, 5].map((value) => (
              <button
                key={value}
                onClick={() => setRating(value)}
                style={{
                  background: 'none',
                  border: 0,
                  cursor: 'pointer',
                  fontSize: 30,
                  color: value <= rating ? 'var(--color-accent-700)' : '#b7b7ba',
                }}
                aria-label={`${value} star`}
              >
                ★
              </button>
            ))}
          </div>
          <div style={{ fontSize: 13, color: '#5d5d60' }}>{RATING_WORD[rating]}</div>
        </div>

        <div className="field">
          <label>What is most important to know?</label>
          <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} />
        </div>

        <div className="field">
          <label>What did you like or dislike? What did you use this product for?</label>
          <textarea className="input" value={text} onChange={(e) => setText(e.target.value)} />
        </div>

        <div className="ph" style={{ height: 96 }}>
          <span>Add a photo or video</span>
        </div>

        <div style={{ display: 'flex', gap: 12 }}>
          <button className="btn btn-primary" onClick={submit} disabled={rating === 0}>
            Submit review
          </button>
          <button className="btn btn-secondary" onClick={() => navigate(`/product/${productId}`)}>
            Cancel
          </button>
        </div>
      </Blueprint>
    </div>
  )
}
