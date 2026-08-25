import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import { useAuth } from '../context/AuthContext'
import { ordersApi, type Order } from '../services/api'
import { DEFAULT_ADDRESS, PAYMENT_OPTIONS, SHIPPING_OPTIONS } from '../lib/constants'
import { usd } from '../lib/format'

interface LocationState {
  shippingLabel?: string
  paymentLabel?: string
}

export default function OrderConfirmation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()
  const [order, setOrder] = useState<Order | null>(null)

  useEffect(() => {
    ordersApi.getById(Number(id)).then(setOrder)
  }, [id])

  if (!order) return <div style={{ maxWidth: 820, margin: '0 auto', padding: 24 }}>Loading…</div>

  const state = (location.state as LocationState) ?? {}
  const shippingLabel = state.shippingLabel ?? SHIPPING_OPTIONS.standard
  const paymentLabel = state.paymentLabel ?? PAYMENT_OPTIONS.card
  const address = `${DEFAULT_ADDRESS.street}, ${DEFAULT_ADDRESS.city} ${DEFAULT_ADDRESS.state} ${DEFAULT_ADDRESS.zip}`

  return (
    <div style={{ maxWidth: 820, margin: '0 auto', padding: 24 }}>
      <Blueprint style={{ padding: 34 }}>
        <div className="kick">Order {order.id}</div>
        <h1 style={{ fontSize: 40 }}>Order placed, thanks.</h1>
        <p style={{ color: '#5d5d60' }}>
          A confirmation was sent to {user?.email ?? 'you'}. Arriving Thursday, August 13.
        </p>

        <table className="table">
          <tbody>
            <tr>
              <td>Order total</td>
              <td>{usd(order.totalAmount)}</td>
            </tr>
            <tr>
              <td>Payment</td>
              <td>{paymentLabel}</td>
            </tr>
            <tr>
              <td>Delivery</td>
              <td>{shippingLabel}</td>
            </tr>
            <tr>
              <td>Address</td>
              <td>{address}</td>
            </tr>
          </tbody>
        </table>

        <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
          <button className="btn btn-primary" onClick={() => navigate('/orders')}>
            View your orders
          </button>
          <button className="btn btn-secondary" onClick={() => navigate('/')}>
            Back to the store
          </button>
        </div>
      </Blueprint>
    </div>
  )
}
