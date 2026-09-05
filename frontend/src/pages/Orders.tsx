import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import Placeholder from '../components/ui/Placeholder'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { ordersApi, type Order } from '../services/api'
import { DELIVERY_DATE_LABEL } from '../lib/constants'
import { usd } from '../lib/format'
import { deriveFastDelivery } from '../lib/mockProductMeta'

export default function Orders() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const cart = useCart()
  const [orders, setOrders] = useState<Order[]>([])

  useEffect(() => {
    if (user) ordersApi.listByBuyer(user.id).then(setOrders)
  }, [user])

  const allIds = orders.flatMap((order) => order.items.map((item) => item.productId))
  const { products } = useProductsByIds(allIds)

  if (!user) {
    return (
      <div style={{ maxWidth: 1080, margin: '0 auto', padding: 24, textAlign: 'center' }}>
        <h1>Sign in to see your orders</h1>
        <button className="btn btn-primary" onClick={() => navigate('/signin')}>
          Sign in
        </button>
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 1080, margin: '0 auto', padding: 24 }}>
      <h1>Your orders</h1>
      <p style={{ fontSize: 13, color: '#5d5d60' }}>{orders.length} order(s) placed in the last 6 months</p>

      {orders.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <h3>No orders yet</h3>
          <button className="btn btn-primary" onClick={() => navigate('/')}>
            Start shopping
          </button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20, marginTop: 16 }}>
          {orders.map((order) => {
            const anyFast = order.items.some((item) => {
              const product = products.get(item.productId)
              return product ? deriveFastDelivery(product) : false
            })
            return (
              <Blueprint key={order.id} style={{ padding: 0 }}>
                <div
                  style={{
                    background: 'var(--color-surface)',
                    padding: 16,
                    display: 'grid',
                    gridTemplateColumns: '1fr 1fr 1fr auto',
                    gap: 16,
                    fontSize: 12,
                  }}
                >
                  <div>
                    <div style={{ textTransform: 'uppercase', letterSpacing: '.1em', color: '#7a7a7d' }}>Order placed</div>
                    <div>{new Date(order.createdAt).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}</div>
                  </div>
                  <div>
                    <div style={{ textTransform: 'uppercase', letterSpacing: '.1em', color: '#7a7a7d' }}>Total</div>
                    <div>{usd(order.totalAmount)}</div>
                  </div>
                  <div>
                    <div style={{ textTransform: 'uppercase', letterSpacing: '.1em', color: '#7a7a7d' }}>Ship to</div>
                    <div>{user.name}</div>
                  </div>
                  <div style={{ textAlign: 'right' }}>Order #{order.id}</div>
                </div>
                <div style={{ padding: 16 }}>
                  <div className="h" style={{ fontSize: 19, color: 'var(--color-accent-700)', marginBottom: 12 }}>
                    {anyFast ? 'Arriving tomorrow' : `Arriving ${DELIVERY_DATE_LABEL}`}
                  </div>
                  {order.items.map((item) => {
                    const product = products.get(item.productId)
                    return (
                      <div key={item.id} style={{ display: 'grid', gridTemplateColumns: '86px 1fr 190px', gap: 12, marginBottom: 12 }}>
                        <div style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${item.productId}`)}>
                          <Placeholder label="Product" aspect="1/1" src={product?.imageUrl} />
                        </div>
                        <div>
                          <div style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${item.productId}`)}>
                            {product?.name ?? `Product #${item.productId}`}
                          </div>
                          <div style={{ fontSize: 12, color: '#7a7a7d' }}>
                            Qty {item.quantity} · {usd(item.unitPrice)}
                          </div>
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                          <button
                            className="btn btn-primary"
                            onClick={() => {
                              if (product) {
                                cart.addItem(product, item.quantity)
                                navigate('/cart')
                              }
                            }}
                            disabled={!product}
                          >
                            Buy it again
                          </button>
                          <button className="btn btn-secondary" onClick={() => navigate(`/product/${item.productId}/review`)}>
                            Write a product review
                          </button>
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
