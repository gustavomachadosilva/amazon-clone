import { Link, useNavigate } from 'react-router-dom'
import { Button, Placeholder } from '../ui'
import { useCart } from '../../context/CartContext'
import { usd } from '../../lib/format'
import type { OrderItem, Product } from '../../services/api'

interface OrderItemRowProps {
  item: OrderItem
  // Undefined while the catalog lookup is loading or if the product no longer exists.
  product?: Product
}

// One purchased line: image and name link to the product, plus "Buy it again" and review actions.
export default function OrderItemRow({ item, product }: OrderItemRowProps) {
  const navigate = useNavigate()
  const cart = useCart()
  const name = product?.name ?? `Product #${item.productId}`
  const productPath = `/product/${item.productId}`

  return (
    <div className="mb-3 grid grid-cols-[70px_1fr] gap-3 sm:grid-cols-[86px_1fr_190px]">
      <Link to={productPath} aria-hidden="true" tabIndex={-1} className="block">
        <Placeholder label={name} aspect="1/1" src={product?.imageUrl} />
      </Link>
      <div className="min-w-0">
        <Link to={productPath} className="block break-words font-medium text-foreground hover:underline">
          {name}
        </Link>
        <div className="text-[15px] text-paper-600">
          Qty {item.quantity} · <span className="readout">{usd(item.unitPrice)}</span>
        </div>
      </div>
      <div className="col-span-2 flex flex-col gap-1.5 sm:col-span-1">
        <Button
          variant="primary"
          onClick={() => {
            if (product) {
              cart.addItem(product, item.quantity)
              navigate('/cart')
            }
          }}
          disabled={!product}
        >
          Buy it again
        </Button>
        <Button variant="secondary" onClick={() => navigate(`${productPath}/review`)}>
          Write a product review
        </Button>
      </div>
    </div>
  )
}
