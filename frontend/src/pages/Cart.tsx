import { Minus, Plus, Trash2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import Placeholder from '../components/ui/Placeholder'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { usd } from '../lib/format'
import { freeShippingMessage } from '../lib/pricing'
import { deriveDeliveryLabel, deriveStockLabel } from '../lib/mockProductMeta'

export default function Cart() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const cart = useCart()

  const allIds = [...cart.items, ...cart.saved].map((line) => line.productId)
  const { products } = useProductsByIds(allIds)

  function proceedToCheckout() {
    navigate(user ? '/checkout' : '/signin')
  }

  return (
    <div style={{ maxWidth: 1280, margin: '0 auto', padding: 24, display: 'grid', gridTemplateColumns: '1fr 300px', gap: 28 }}>
      <section>
        <h1>Shopping cart</h1>
        <p style={{ fontSize: 13, color: '#5d5d60' }}>
          {cart.items.length} product(s) · prices and availability may change
        </p>
        <div className="hr" />

        {cart.items.length === 0 ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <h3>Your cart is empty</h3>
            <button className="btn btn-primary" onClick={() => navigate('/')}>
              Continue shopping
            </button>
          </div>
        ) : (
          cart.items.map((line) => {
            const product = products.get(line.productId)
            return (
              <div
                key={line.productId}
                style={{
                  display: 'grid',
                  gridTemplateColumns: '130px 1fr auto',
                  gap: 16,
                  padding: '16px 0',
                  borderBottom: '1px solid var(--color-divider)',
                }}
              >
                <div style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${line.productId}`)}>
                  <Placeholder label="Product" aspect="1/1" src={product?.imageUrl} />
                </div>
                <div>
                  <div style={{ cursor: 'pointer' }} onClick={() => navigate(`/product/${line.productId}`)}>
                    {line.name}
                  </div>
                  {product && (
                    <>
                      <div style={{ fontSize: 13, color: 'var(--color-accent-700)' }}>{deriveStockLabel(product)}</div>
                      <div style={{ fontSize: 12.5, color: '#5d5d60' }}>{deriveDeliveryLabel(product)}</div>
                    </>
                  )}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 8 }}>
                    <div style={{ display: 'flex', alignItems: 'center', border: '1px solid var(--color-divider)' }}>
                      <button className="btn btn-icon" style={{ border: 0 }} onClick={() => cart.decrementQty(line.productId)}>
                        <Minus size={14} strokeWidth={1.5} />
                      </button>
                      <span style={{ padding: '0 10px' }}>{line.qty}</span>
                      <button className="btn btn-icon" style={{ border: 0 }} onClick={() => cart.incrementQty(line.productId)}>
                        <Plus size={14} strokeWidth={1.5} />
                      </button>
                    </div>
                    <button className="btn btn-ghost" onClick={() => cart.removeItem(line.productId)}>
                      <Trash2 size={14} strokeWidth={1.5} /> Delete
                    </button>
                    <button className="btn btn-ghost" onClick={() => cart.saveForLater(line.productId)}>
                      Save for later
                    </button>
                  </div>
                </div>
                <div style={{ textAlign: 'right', fontSize: 20 }} className="h">
                  {usd(line.price * line.qty)}
                </div>
              </div>
            )
          })
        )}

        {cart.items.length > 0 && (
          <div style={{ textAlign: 'right', fontSize: 20, marginTop: 16 }} className="h">
            Subtotal ({cart.itemCount} items): {usd(cart.subtotal)}
          </div>
        )}

        {cart.saved.length > 0 && (
          <div style={{ marginTop: 40 }}>
            <h2>Saved for later</h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16 }}>
              {cart.saved.map((line) => (
                <Blueprint key={line.productId} style={{ padding: 12 }}>
                  <Placeholder label="Product" aspect="1/1" src={products.get(line.productId)?.imageUrl} />
                  <div style={{ fontSize: 13, minHeight: 32, marginTop: 8 }}>{line.name}</div>
                  <div className="h">{usd(line.price)}</div>
                  <button className="btn btn-secondary btn-block" onClick={() => cart.moveToCart(line.productId)}>
                    Move to cart
                  </button>
                </Blueprint>
              ))}
            </div>
          </div>
        )}
      </section>

      <Blueprint as="aside" style={{ padding: 18, position: 'sticky', top: 16, height: 'fit-content' }}>
        <div style={{ fontSize: 13, color: 'var(--color-accent-700)', marginBottom: 8 }}>
          {freeShippingMessage(cart.subtotal)}
        </div>
        <div style={{ fontSize: 13 }}>Subtotal ({cart.itemCount} items):</div>
        <div className="h" style={{ fontSize: 26 }}>
          {usd(cart.subtotal)}
        </div>
        <label className="radio" style={{ display: 'flex', margin: '12px 0' }}>
          <input type="checkbox" />
          <span className="box" />
          This order contains a gift
        </label>
        <button className="btn btn-primary btn-block" onClick={proceedToCheckout} disabled={cart.items.length === 0}>
          Proceed to checkout
        </button>
        <button className="btn btn-secondary btn-block" onClick={() => navigate('/')}>
          Continue shopping
        </button>
      </Blueprint>
    </div>
  )
}
