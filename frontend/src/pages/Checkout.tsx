import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import Placeholder from '../components/ui/Placeholder'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { ordersApi } from '../services/api'
import { DEFAULT_ADDRESS, PAYMENT_OPTIONS, SHIPPING_OPTIONS } from '../lib/constants'
import { usd } from '../lib/format'
import { computeCheckoutTotals } from '../lib/pricing'

type ShippingMethod = keyof typeof SHIPPING_OPTIONS
type PaymentMethod = keyof typeof PAYMENT_OPTIONS

export default function Checkout() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const cart = useCart()

  const allIds = cart.items.map((line) => line.productId)
  const { products } = useProductsByIds(allIds)

  const [address, setAddress] = useState({
    fullName: user?.name ?? '',
    zip: DEFAULT_ADDRESS.zip,
    street: DEFAULT_ADDRESS.street,
    city: DEFAULT_ADDRESS.city,
    state: DEFAULT_ADDRESS.state,
  })
  const [shipping, setShipping] = useState<ShippingMethod>('standard')
  const [payment, setPayment] = useState<PaymentMethod>('card')
  const [placing, setPlacing] = useState(false)

  useEffect(() => {
    if (!user) navigate('/signin')
  }, [user, navigate])

  if (!user) return null

  const totals = computeCheckoutTotals(cart.subtotal, shipping)

  async function placeOrder() {
    setPlacing(true)
    try {
      const order = await ordersApi.checkout(
        user!.id,
        cart.items.map((line) => ({ productId: line.productId, quantity: line.qty })),
      )
      cart.clear()
      navigate(`/order/${order.id}`, {
        state: { shippingLabel: SHIPPING_OPTIONS[shipping], paymentLabel: PAYMENT_OPTIONS[payment] },
      })
    } finally {
      setPlacing(false)
    }
  }

  return (
    <div style={{ maxWidth: 1080, margin: '0 auto', padding: 24, display: 'grid', gridTemplateColumns: '1fr 300px', gap: 28 }}>
      <section style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <h1>Checkout</h1>

        <Blueprint style={{ padding: 20 }}>
          <div className="kick">1 · Shipping address</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 12 }}>
            <div className="field">
              <label>Full name</label>
              <input className="input" value={address.fullName} onChange={(e) => setAddress({ ...address, fullName: e.target.value })} />
            </div>
            <div className="field">
              <label>ZIP code</label>
              <input className="input" value={address.zip} onChange={(e) => setAddress({ ...address, zip: e.target.value })} />
            </div>
            <div className="field" style={{ gridColumn: 'span 2' }}>
              <label>Street address</label>
              <input className="input" value={address.street} onChange={(e) => setAddress({ ...address, street: e.target.value })} />
            </div>
            <div className="field">
              <label>City</label>
              <input className="input" value={address.city} onChange={(e) => setAddress({ ...address, city: e.target.value })} />
            </div>
            <div className="field">
              <label>State</label>
              <input className="input" value={address.state} onChange={(e) => setAddress({ ...address, state: e.target.value })} />
            </div>
          </div>
        </Blueprint>

        <Blueprint style={{ padding: 20 }}>
          <div className="kick">2 · Delivery option</div>
          <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
            {(Object.keys(SHIPPING_OPTIONS) as ShippingMethod[]).map((key) => (
              <label key={key} className="radio" style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input type="radio" name="shipping" checked={shipping === key} onChange={() => setShipping(key)} />
                  <span className="dot" />
                  {SHIPPING_OPTIONS[key]}
                </span>
                <span>{key === 'express' ? usd(9.99) : 'FREE'}</span>
              </label>
            ))}
          </div>
        </Blueprint>

        <Blueprint style={{ padding: 20 }}>
          <div className="kick">3 · Payment method</div>
          <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
            {(Object.keys(PAYMENT_OPTIONS) as PaymentMethod[]).map((key) => (
              <label key={key} className="radio" style={{ display: 'flex' }}>
                <input type="radio" name="payment" checked={payment === key} onChange={() => setPayment(key)} />
                <span className="dot" />
                {PAYMENT_OPTIONS[key]}
              </label>
            ))}
          </div>
        </Blueprint>

        <Blueprint style={{ padding: 20 }}>
          <div className="kick">4 · Review items</div>
          <div style={{ marginTop: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
            {cart.items.map((line) => (
              <div key={line.productId} style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                <Placeholder label="Product" aspect="1/1" className="w-[56px]" src={products.get(line.productId)?.imageUrl} />
                <div style={{ flex: 1 }}>
                  <div>{line.name}</div>
                  <div style={{ fontSize: 12, color: '#7a7a7d' }}>Qty {line.qty}</div>
                </div>
                <div className="h">{usd(line.price * line.qty)}</div>
              </div>
            ))}
          </div>
        </Blueprint>
      </section>

      <Blueprint as="aside" style={{ padding: 18, position: 'sticky', top: 16, height: 'fit-content' }}>
        <div className="h" style={{ fontSize: 16, marginBottom: 8 }}>
          Order summary
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
          <span>Items ({cart.itemCount})</span>
          <span>{usd(cart.subtotal)}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
          <span>Shipping</span>
          <span>{usd(totals.shipping)}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
          <span>Estimated tax</span>
          <span>{usd(totals.tax)}</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
          <span>Promotion</span>
          <span>-{usd(totals.discount)}</span>
        </div>
        <div className="hr" />
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span className="h">Order total</span>
          <span className="h" style={{ fontSize: 26, color: 'var(--color-accent-800)' }}>
            {usd(totals.total)}
          </span>
        </div>
        <button className="btn btn-primary btn-block" onClick={placeOrder} disabled={placing || cart.items.length === 0}>
          Place your order
        </button>
        <p style={{ fontSize: 11.5, color: '#7a7a7d', marginTop: 8 }}>
          By placing your order you agree to the terms of this academic prototype.
        </p>
      </Blueprint>
    </div>
  )
}
