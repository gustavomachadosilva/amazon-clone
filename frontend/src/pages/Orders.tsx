import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Blueprint, Button, Placeholder } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { ordersApi, type Order } from '../services/api'
import { DELIVERY_DATE_LABEL } from '../lib/constants'
import { usd } from '../lib/format'
import { deriveFastDelivery } from '../lib/mockProductMeta'
import { onEnterKey } from '../lib/a11y'

export default function Orders() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const cart = useCart()
  const [orders, setOrders] = useState<Order[]>([])

  useEffect(() => {
    if (user) ordersApi.listByBuyer().then(setOrders)
  }, [user])

  const allIds = orders.flatMap((order) => order.items.map((item) => item.productId))
  const { products } = useProductsByIds(allIds)

  if (!user) {
    return (
      <div className="mx-auto max-w-[1320px] px-4 py-4 text-center md:px-6 md:py-6">
        <h1>Sign in to see your orders</h1>
        <Button variant="primary" onClick={() => navigate('/signin')}>
          Sign in
        </Button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1320px] px-4 py-4 md:px-6 md:py-6">
      <h1>Your orders</h1>
      <p className="text-[16.5px] text-paper-700">{orders.length} order(s) placed in the last 6 months</p>

      {orders.length === 0 ? (
        <div className="p-10 text-center">
          <h3>No orders yet</h3>
          <Button variant="primary" onClick={() => navigate('/')}>
            Start shopping
          </Button>
        </div>
      ) : (
        <div className="mt-4 flex flex-col gap-5">
          {orders.map((order) => {
            const anyFast = order.items.some((item) => {
              const product = products.get(item.productId)
              return product ? deriveFastDelivery(product) : false
            })
            return (
              <Blueprint key={order.id} className="p-0">
                <div className="grid grid-cols-2 gap-3 bg-surface p-4 text-xs sm:grid-cols-[1fr_1fr_1fr_auto] sm:gap-4">
                  <div>
                    <div className="uppercase tracking-[.1em] text-paper-600">Order placed</div>
                    <div>{new Date(order.createdAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</div>
                  </div>
                  <div>
                    <div className="uppercase tracking-[.1em] text-paper-600">Total</div>
                    <div className="readout font-semibold">{usd(order.totalAmount)}</div>
                  </div>
                  <div>
                    <div className="uppercase tracking-[.1em] text-paper-600">Ship to</div>
                    <div>{order.address.fullName}</div>
                    <div className="text-paper-600">
                      {order.address.street}, {order.address.city} {order.address.state} {order.address.zip}
                    </div>
                  </div>
                  <div className="sm:text-right">Order #{order.id}</div>
                </div>
                <div className="p-4">
                  <div className="h mb-3 text-[24px] text-accent-700">
                    {anyFast ? 'Arriving tomorrow' : `Arriving ${DELIVERY_DATE_LABEL}`}
                  </div>
                  {order.items.map((item) => {
                    const product = products.get(item.productId)
                    return (
                      <div key={item.id} className="mb-3 grid grid-cols-[70px_1fr] gap-3 sm:grid-cols-[86px_1fr_190px]">
                        <div
                          className="cursor-pointer"
                          role="link"
                          tabIndex={0}
                          onClick={() => navigate(`/product/${item.productId}`)}
                          onKeyDown={onEnterKey(() => navigate(`/product/${item.productId}`))}
                        >
                          <Placeholder label={product?.name ?? `Product #${item.productId}`} aspect="1/1" src={product?.imageUrl} />
                        </div>
                        <div>
                          <div
                            className="cursor-pointer"
                            role="link"
                            tabIndex={0}
                            onClick={() => navigate(`/product/${item.productId}`)}
                            onKeyDown={onEnterKey(() => navigate(`/product/${item.productId}`))}
                          >
                            {product?.name ?? `Product #${item.productId}`}
                          </div>
                          <div className="text-xs text-paper-600">
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
                          <Button variant="secondary" onClick={() => navigate(`/product/${item.productId}/review`)}>
                            Write a product review
                          </Button>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </Blueprint>
            )
          })}
        </div>
      )}
    </div>
  )
}
