import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Blueprint, Button } from '../components/ui'
import OrderItemRow from '../components/orders/OrderItemRow'
import { useAuth } from '../context/AuthContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { ordersApi, type Order } from '../services/api'
import { usd } from '../lib/format'
import { HEADLINE_TONE_CLASS, deliveryHeadline, formatOrderDate } from '../lib/orderStatus'

export default function Orders() {
  const navigate = useNavigate()
  const { user } = useAuth()
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
            const headline = deliveryHeadline(order)
            return (
              <Blueprint key={order.id} className="p-0">
                <div className="grid grid-cols-2 gap-3 bg-surface p-4 text-[16.5px] sm:grid-cols-[1fr_1fr_1fr_auto] sm:gap-4">
                  <div className="min-w-0">
                    <div className="field-label">Order placed</div>
                    <div className="break-words">{formatOrderDate(order.createdAt)}</div>
                  </div>
                  <div className="min-w-0">
                    <div className="field-label">Total</div>
                    <div className="readout font-semibold">{usd(order.totalAmount)}</div>
                  </div>
                  <div className="min-w-0">
                    <div className="field-label">Ship to</div>
                    {order.address ? (
                      <>
                        <div className="break-words">{order.address.fullName}</div>
                        <div className="break-words text-paper-600">
                          {order.address.street}, {order.address.city} {order.address.state} {order.address.zip}
                        </div>
                      </>
                    ) : (
                      <div className="break-words text-paper-600">Not available</div>
                    )}
                  </div>
                  <div className="col-span-2 flex min-w-0 flex-col gap-1 break-words sm:col-span-1 sm:text-right">
                    <Link to={`/orders/${order.id}`} className="font-medium">
                      Order #{order.id}
                    </Link>
                    <Link to={`/orders/${order.id}`} className="text-[15px]" aria-label={`View order details for order #${order.id}`}>
                      View order details
                    </Link>
                  </div>
                </div>
                <div className="p-4">
                  <div className={`h mb-3 text-[24px] ${HEADLINE_TONE_CLASS[headline.tone]}`}>{headline.text}</div>
                  {order.items.map((item) => (
                    <OrderItemRow key={item.id} item={item} product={products.get(item.productId)} />
                  ))}
                </div>
              </Blueprint>
            )
          })}
        </div>
      )}
    </div>
  )
}
