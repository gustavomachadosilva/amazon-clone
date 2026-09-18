import { useNavigate } from 'react-router-dom'
import { Button, Blueprint, Placeholder, StarRating } from './ui'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useReviews } from '../context/ReviewsContext'
import { truncate, usd } from '../lib/format'
import { deriveDeliveryLabel, deriveListPrice } from '../lib/mockProductMeta'
import { onEnterKey } from '../lib/a11y'
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

  function stopKeyPropagation(event: React.KeyboardEvent) {
    event.stopPropagation()
  }

  const inStock = product.stockQuantity > 0
  const lowStock = inStock && product.stockQuantity <= 5

  const lineCode = `LN-${String(product.id).padStart(4, '0')}`
  const displayName = truncate(product.name, compact ? 40 : 80)

  return (
    <Blueprint
      as="div"
      className="prod flex cursor-pointer flex-col overflow-hidden bg-card"
      role="link"
      tabIndex={0}
      aria-label={`View ${product.name}`}
      onClick={open}
      onKeyDown={onEnterKey(open)}
    >
      <div className="flex items-center justify-between bg-surface px-3 py-1.5">
        <span className="readout text-[14px] text-paper-600">{lineCode}</span>
        <span className="truncate pl-2 font-mono text-[13px] uppercase tracking-wide text-paper-600">
          {product.category}
        </span>
      </div>

      <div className="p-3">
        <Placeholder label={product.name} aspect="1/1" src={product.imageUrl} />
      </div>

      <div className="border-t border-dashed border-divider px-3 pt-3">
        <div className={`text-[18px] leading-[1.3] ${compact ? '' : 'line-clamp-2 min-h-[47px]'}`}>{displayName}</div>
        <div className="mt-1.5 flex items-center gap-1.5 text-sm">
          <StarRating rating={rating} size={17} />
          <span className="readout text-paper-600">{productReviews.length.toLocaleString('en-US')}</span>
        </div>
      </div>

      <div className="mt-3 flex flex-col gap-2 border-t border-dashed border-divider px-3 pb-3 pt-3">
        <div className="flex flex-wrap items-baseline justify-between gap-x-2 gap-y-1">
          <div className="flex items-baseline gap-2">
            <span className={`readout font-semibold text-foreground ${compact ? 'text-[25px]' : 'text-[29px]'}`}>
              {usd(product.price)}
            </span>
            {listPrice > product.price && (
              <span className="readout text-[15px] text-paper-500 line-through">{usd(listPrice)}</span>
            )}
          </div>
          <span className={`stamp ${lowStock ? 'stamp-alert' : ''}`}>
            {inStock ? (lowStock ? 'Low stock' : 'In stock') : 'Sold out'}
          </span>
        </div>
        {!compact && <div className="text-[16px] text-paper-600">{deriveDeliveryLabel(product)}</div>}
        {!compact && (
          <Button variant="primary" block onClick={addToCart} onKeyDown={stopKeyPropagation} disabled={!inStock}>
            {inStock ? 'Add to cart' : 'Out of stock'}
          </Button>
        )}
      </div>
    </Blueprint>
  )
}
