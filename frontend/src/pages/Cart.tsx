import { Minus, Plus, Trash2 } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Blueprint, Button, Placeholder } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useCart } from '../context/CartContext'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { usd } from '../lib/format'
import { freeShippingMessage } from '../lib/pricing'
import { deriveDeliveryLabel, deriveStockLabel } from '../lib/mockProductMeta'
import { onEnterKey } from '../lib/a11y'

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
    <div className="grid w-full grid-cols-1 gap-6 px-4 py-4 md:grid-cols-[1fr_300px] md:gap-7 md:px-8 md:py-6 lg:px-10">
      <section>
        <h1>Shopping cart</h1>
        <p className="text-[16.5px] text-paper-700">
          {cart.items.length} product(s) · prices and availability may change
        </p>
        <div className="hr" />

        {cart.items.length === 0 ? (
          <div className="p-10 text-center">
            <h3>Your cart is empty</h3>
            <Button variant="primary" onClick={() => navigate('/')}>
              Continue shopping
            </Button>
          </div>
        ) : (
          cart.items.map((line) => {
            const product = products.get(line.productId)
            return (
              <div
                key={line.productId}
                className="grid grid-cols-[100px_1fr] gap-4 border-b border-divider py-4 sm:grid-cols-[130px_1fr_auto]"
              >
                <div
                  className="cursor-pointer"
                  role="link"
                  tabIndex={0}
                  onClick={() => navigate(`/product/${line.productId}`)}
                  onKeyDown={onEnterKey(() => navigate(`/product/${line.productId}`))}
                >
                  <Placeholder label={line.name} aspect="1/1" src={product?.imageUrl} />
                </div>
                <div>
                  <div
                    className="cursor-pointer"
                    role="link"
                    tabIndex={0}
                    onClick={() => navigate(`/product/${line.productId}`)}
                    onKeyDown={onEnterKey(() => navigate(`/product/${line.productId}`))}
                  >
                    {line.name}
                  </div>
                  {product && (
                    <>
                      <div className="text-[16.5px] text-accent-700">{deriveStockLabel(product)}</div>
                      <div className="text-[16px] text-paper-700">{deriveDeliveryLabel()}</div>
                    </>
                  )}
                  <div className="mt-2 flex flex-wrap items-center gap-3">
                    <div className="flex items-center border border-divider">
                      <Button
                        variant="icon"
                        className="border-0"
                        aria-label="Decrease quantity"
                        onClick={() => cart.decrementQty(line.productId)}
                      >
                        <Minus size={17} strokeWidth={1.5} />
                      </Button>
                      <span className="px-2.5">{line.qty}</span>
                      <Button
                        variant="icon"
                        className="border-0"
                        aria-label="Increase quantity"
                        onClick={() => cart.incrementQty(line.productId)}
                      >
                        <Plus size={17} strokeWidth={1.5} />
                      </Button>
                    </div>
                    <Button variant="ghost" onClick={() => cart.removeItem(line.productId)}>
                      <Trash2 size={17} strokeWidth={1.5} /> Delete
                    </Button>
                    <Button variant="ghost" onClick={() => cart.saveForLater(line.productId)}>
                      Save for later
                    </Button>
                  </div>
                </div>
                <div className="readout col-span-2 text-left text-xl font-semibold sm:col-span-1 sm:text-right">
                  {usd(line.price * line.qty)}
                </div>
              </div>
            )
          })
        )}

        {cart.items.length > 0 && (
          <div className="readout mt-4 text-right text-xl font-semibold">
            Subtotal ({cart.itemCount} items): {usd(cart.subtotal)}
          </div>
        )}

        {cart.saved.length > 0 && (
          <div className="mt-10">
            <h2>Saved for later</h2>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
              {cart.saved.map((line) => (
                <Blueprint key={line.productId} className="p-3">
                  <Placeholder label={line.name} aspect="1/1" src={products.get(line.productId)?.imageUrl} />
                  <div className="mt-2 min-h-8 text-[16.5px]">{line.name}</div>
                  <div className="readout font-semibold">{usd(line.price)}</div>
                  <Button variant="secondary" block onClick={() => cart.moveToCart(line.productId)}>
                    Move to cart
                  </Button>
                </Blueprint>
              ))}
            </div>
          </div>
        )}
      </section>

      <Blueprint as="aside" className="h-fit p-4 md:sticky md:top-4 md:p-[18px]">
        <div className="mb-2 text-[16.5px] text-accent-700">{freeShippingMessage(cart.subtotal)}</div>
        <div className="text-[16.5px]">Subtotal ({cart.itemCount} items):</div>
        <div className="readout text-2xl font-semibold">{usd(cart.subtotal)}</div>
        <label className="radio my-3 flex">
          <input type="checkbox" />
          <span className="box" />
          This order contains a gift
        </label>
        <Button variant="primary" block onClick={proceedToCheckout} disabled={cart.items.length === 0}>
          Proceed to checkout
        </Button>
        <Button variant="secondary" block onClick={() => navigate('/')}>
          Continue shopping
        </Button>
      </Blueprint>
    </div>
  )
}
