import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Blueprint, Button, Placeholder } from '../components/ui'
import AddressFields from '../components/orders/AddressFields'
import PaymentMethodOptions from '../components/orders/PaymentMethodOptions'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { ApiRequestError, ordersApi, type OrderAddress, type PaymentMethod } from '../services/api'
import { normalizeAddress, validateAddress, type AddressErrors } from '../lib/address'
import { DEFAULT_ADDRESS, SHIPPING_OPTIONS } from '../lib/constants'
import { usd } from '../lib/format'
import { computeCheckoutTotals } from '../lib/pricing'

type ShippingMethod = keyof typeof SHIPPING_OPTIONS

function generateFallbackKey(): string {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

export default function Checkout() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const cart = useCart()

  const allIds = cart.items.map((line) => line.productId)
  const { products } = useProductsByIds(allIds)

  const [address, setAddress] = useState<OrderAddress>({
    fullName: user?.name ?? '',
    zip: DEFAULT_ADDRESS.zip,
    street: DEFAULT_ADDRESS.street,
    city: DEFAULT_ADDRESS.city,
    state: DEFAULT_ADDRESS.state,
  })
  const [addressErrors, setAddressErrors] = useState<AddressErrors>({})
  const [shipping, setShipping] = useState<ShippingMethod>('STANDARD')
  const [payment, setPayment] = useState<PaymentMethod>('CARD')
  const [placing, setPlacing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [idempotencyKey] = useState(() => crypto.randomUUID?.() ?? generateFallbackKey())

  useEffect(() => {
    if (!user) navigate('/signin')
  }, [user, navigate])

  if (!user) return null

  const totals = computeCheckoutTotals(cart.subtotal, shipping)

  async function placeOrder() {
    setError(null)
    const normalized = normalizeAddress(address)
    const errors = validateAddress(normalized)
    setAddressErrors(errors)
    if (Object.keys(errors).length > 0) return

    setPlacing(true)
    try {
      const order = await ordersApi.checkout(
        {
          items: cart.items.map((line) => ({ productId: line.productId, quantity: line.qty })),
          address: normalized,
          shippingMethod: shipping,
          paymentMethod: payment,
        },
        idempotencyKey
      )
      if (order.status !== 'PAID') {
        setError('Your payment was declined. Please check your payment method and try again.')
        return
      }
      cart.clear()
      navigate(`/order/${order.id}`)
    } catch (err) {
      setError(
        err instanceof ApiRequestError && err.apiMessage
          ? err.apiMessage
          : 'We could not place your order. Check your connection and try again.'
      )
    } finally {
      setPlacing(false)
    }
  }

  return (
    <div className="mx-auto grid max-w-[1320px] grid-cols-1 gap-6 px-4 py-4 md:grid-cols-[1fr_300px] md:gap-7 md:px-6 md:py-6">
      <section className="flex flex-col gap-5">
        <h1>Checkout</h1>

        <Blueprint className="p-4 md:p-5">
          <div className="mb-1 flex items-center gap-3">
            <span className="stamp shrink-0 !rotate-0">01</span>
            <h2 className="text-[24px]">Shipping address</h2>
          </div>
          <div className="mt-3">
            <AddressFields value={address} onChange={setAddress} errors={addressErrors} disabled={placing} />
          </div>
        </Blueprint>

        <Blueprint className="p-4 md:p-5">
          <div className="mb-1 flex items-center gap-3">
            <span className="stamp shrink-0 !rotate-0">02</span>
            <h2 className="text-[24px]">Delivery option</h2>
          </div>
          <div className="mt-3 flex flex-col gap-2">
            {(Object.keys(SHIPPING_OPTIONS) as ShippingMethod[]).map((key) => (
              <label key={key} className="radio flex justify-between">
                <span className="flex items-center gap-2">
                  <input type="radio" name="shipping" checked={shipping === key} onChange={() => setShipping(key)} />
                  <span className="dot" />
                  {SHIPPING_OPTIONS[key]}
                </span>
                <span className="readout">{key === 'EXPRESS' ? usd(9.99) : 'FREE'}</span>
              </label>
            ))}
          </div>
        </Blueprint>

        <Blueprint className="p-4 md:p-5">
          <div className="mb-1 flex items-center gap-3">
            <span className="stamp shrink-0 !rotate-0">03</span>
            <h2 className="text-[24px]">Payment method</h2>
          </div>
          <div className="mt-3">
            <PaymentMethodOptions name="payment" value={payment} onChange={setPayment} disabled={placing} />
          </div>
        </Blueprint>

        <Blueprint className="p-4 md:p-5">
          <div className="mb-1 flex items-center gap-3">
            <span className="stamp shrink-0 !rotate-0">04</span>
            <h2 className="text-[24px]">Review items</h2>
          </div>
          <div className="mt-3 flex flex-col gap-3">
            {cart.items.map((line) => (
              <div key={line.productId} className="flex items-center gap-3">
                <Placeholder label={line.name} aspect="1/1" className="w-[56px] flex-none" src={products.get(line.productId)?.imageUrl} />
                <div className="min-w-0 flex-1">
                  <div className="truncate">{line.name}</div>
                  <div className="whitespace-nowrap text-xs text-paper-600">Qty {line.qty}</div>
                </div>
                <div className="readout flex-none whitespace-nowrap font-semibold">{usd(line.price * line.qty)}</div>
              </div>
            ))}
          </div>
        </Blueprint>
      </section>

      <Blueprint as="aside" className="h-fit p-4 md:sticky md:top-4 md:p-[18px]">
        <h2 className="mb-2 text-[19px]">Order summary</h2>
        <div className="flex justify-between text-[16.5px]">
          <span>Items ({cart.itemCount})</span>
          <span className="readout">{usd(cart.subtotal)}</span>
        </div>
        <div className="flex justify-between text-[16.5px]">
          <span>Shipping</span>
          <span className="readout">{usd(totals.shipping)}</span>
        </div>
        <div className="flex justify-between text-[16.5px]">
          <span>Estimated tax</span>
          <span className="readout">{usd(totals.tax)}</span>
        </div>
        <div className="flex justify-between text-[16.5px]">
          <span>Promotion</span>
          <span className="readout">-{usd(totals.discount)}</span>
        </div>
        <div className="hr" />
        <div className="perf-top flex justify-between">
          <span className="h">Order total</span>
          <span className="readout text-[33px] font-semibold text-accent-800">{usd(totals.total)}</span>
        </div>
        {error && (
          <div role="alert" className="callout-alert mt-3">
            {error} Your cart is unchanged — try again when you're ready.
          </div>
        )}
        <Button
          variant="primary"
          block
          onClick={placeOrder}
          disabled={placing || cart.items.length === 0}
          className={error ? 'mt-2' : 'mt-3'}
        >
          {placing ? 'Placing order…' : error ? 'Try again' : 'Place your order'}
        </Button>
        <p className="mt-2 text-[15px] text-paper-600">
          By placing your order you agree to the terms of this academic prototype.
        </p>
      </Blueprint>
    </div>
  )
}
