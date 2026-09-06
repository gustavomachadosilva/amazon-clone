import { useNavigate } from 'react-router-dom'
import { Button, Blueprint, Placeholder, StarRating } from './ui'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useReviews } from '../context/ReviewsContext'
import { usd } from '../lib/format'
import { deriveDeliveryLabel, deriveListPrice } from '../lib/mockProductMeta'
import type { Product } from '../services/api'

interface ProductGridCardProps {
  product: Product
  compact?: boolean
}

export default function ProductGridCard({ product, compact = false }: ProductGridCardProps) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const cart = useCart()
  const reviews = useReviews()

  const productReviews = reviews.getReviews(product.id)
  const rating = productReviews.reduce((sum, r) => sum + r.stars, 0) / productReviews.length
  const listPrice = deriveListPrice(product)

  function open() {
    navigate(`/product/${product.id}`)
  }

  function addToCart(event: React.MouseEvent) {
    event.stopPropagation()
    if (!user) {
      navigate('/signin')
      return
    }
    cart.addItem(product)
    navigate('/cart')
  }

  return (
    <Blueprint
      className="prod"
      style={{ padding: '14px', display: 'flex', flexDirection: 'column', gap: '8px', cursor: 'pointer' }}
      onClick={open}
    >
      <Placeholder label="Product" aspect="1/1" src={product.imageUrl} />
      <div style={{ fontSize: '13.5px', lineHeight: 1.3, minHeight: compact ? undefined : '36px' }}>
        {product.name}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px' }}>
        <StarRating rating={rating} />
        <span style={{ color: '#7a7a7d' }}>{productReviews.length.toLocaleString('en-US')}</span>
      </div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
        <span className="h" style={{ fontSize: compact ? '19px' : '22px' }}>
          {usd(product.price)}
        </span>
        {listPrice > product.price && (
          <span style={{ fontSize: '12px', color: '#98989b', textDecoration: 'line-through' }}>
            {usd(listPrice)}
          </span>
        )}
      </div>
      {!compact && <div style={{ fontSize: '11.5px', color: '#5d5d60' }}>{deriveDeliveryLabel(product)}</div>}
      {!compact && (
        <Button variant="primary" block onClick={addToCart}>
          Add to cart
        </Button>
      )}
    </Blueprint>
  )
}
