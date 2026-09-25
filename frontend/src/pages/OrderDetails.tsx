import { useEffect, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Blueprint, Button, Table, TableBody, TableCell, TableRow } from '../components/ui'
import OrderItemRow from '../components/orders/OrderItemRow'
import { useProductsByIds } from '../hooks/useProductsByIds'
import { PAYMENT_OPTIONS, SHIPPING_OPTIONS } from '../lib/constants'
import { usd } from '../lib/format'
import {
  deliveryHeadline,
  formatOrderDate,
  paymentBadge,
  shipmentBadge,
  timelineSteps,
  HEADLINE_TONE_CLASS,
} from '../lib/orderStatus'
import { ApiRequestError, ordersApi, type Order } from '../services/api'

type LoadKind = 'ok' | 'not-found' | 'forbidden' | 'error'

interface LoadResult {
  // The request this result belongs to, so a stale result never shows for another id or retry.
  forKey: string
  kind: LoadKind
  order: Order | null
}

const PAGE_CLASS = 'mx-auto max-w-[1320px] px-4 py-4 md:px-6 md:py-6'

function BackToOrders() {
  return (
    <Link to="/orders" className="text-accent-700 hover:underline">
      ← Back to your orders
    </Link>
  )
}

function DeliveryTimeline({ order }: { order: Order }) {
  const headline = deliveryHeadline(order)
  const steps = timelineSteps(order)

  return (
    <Blueprint as="section" aria-labelledby="delivery-heading" className="bg-card p-4 md:p-6">
      <h2 id="delivery-heading" className={`h text-[24px] ${HEADLINE_TONE_CLASS[headline.tone]}`}>
        {headline.text}
      </h2>
      <ol className="mt-4 flex flex-col gap-3 sm:grid sm:grid-cols-4 sm:gap-4">
        {steps.map((step) => {
          const reached = step.state !== 'upcoming'
          return (
            <li
              key={step.key}
              aria-current={step.state === 'current' ? 'step' : undefined}
              className={`min-w-0 border-l-4 pl-3 sm:border-l-0 sm:border-t-4 sm:pl-0 sm:pt-2 ${
                reached ? 'border-accent-600' : 'border-paper-300'
              }`}
            >
              <div className={`font-medium ${reached ? 'text-foreground' : 'text-paper-600'}`}>
                {step.label}
                {reached && <span className="sr-only"> (completed)</span>}
              </div>
              {step.date && (
                <div className="break-words text-[15px] text-paper-700">
                  {step.estimated ? `Estimated ${step.date}` : step.date}
                </div>
              )}
            </li>
          )
        })}
      </ol>
    </Blueprint>
  )
}

function SummaryField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <div className="field-label">{label}</div>
      <div className="break-words">{children}</div>
    </div>
  )
}

function OrderSummary({ order }: { order: Order }) {
  const itemsSubtotal = order.items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)
  const shippingLabel = order.shippingMethod ? SHIPPING_OPTIONS[order.shippingMethod] : 'Not available'
  const paymentLabel = order.paymentMethod ? PAYMENT_OPTIONS[order.paymentMethod] : 'Not available'

  return (
    <Blueprint as="aside" aria-label="Order summary" className="flex flex-col gap-4 bg-card p-4 md:p-6">
      <SummaryField label="Ship to">
        {order.address ? (
          <>
            <div>{order.address.fullName}</div>
            <div className="text-paper-700">
              {order.address.street}, {order.address.city} {order.address.state} {order.address.zip}
            </div>
          </>
        ) : (
          <span className="text-paper-600">Not available</span>
        )}
      </SummaryField>
      <SummaryField label="Shipping">{shippingLabel}</SummaryField>
      <SummaryField label="Payment">{paymentLabel}</SummaryField>
      <Table>
        <TableBody>
          <TableRow>
            <TableCell>Items subtotal</TableCell>
            <TableCell className="readout text-right">{usd(itemsSubtotal)}</TableCell>
          </TableRow>
          <TableRow>
            <TableCell>Order total</TableCell>
            <TableCell className="readout text-right font-semibold">{usd(order.totalAmount)}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </Blueprint>
  )
}

function LoadedOrder({ order }: { order: Order }) {
  const { products } = useProductsByIds(order.items.map((item) => item.productId))
  const payment = paymentBadge(order.status)
  const shipment = shipmentBadge(order)

  return (
    <>
      <BackToOrders />
      <header className="mt-2 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          <h1 className="break-words">Order #{order.id}</h1>
          <p className="text-[16.5px] text-paper-700">
            Placed {formatOrderDate(order.createdAt)} · Total{' '}
            <span className="readout font-semibold">{usd(order.totalAmount)}</span>
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <span className={payment.className}>{payment.label}</span>
          {shipment && <span className={shipment.className}>{shipment.label}</span>}
        </div>
      </header>

      {order.status === 'FAILED' && (
        <div role="alert" className="callout-alert mt-4 flex-col items-start">
          <strong>Payment failed</strong>
          <span>We couldn't charge your payment method, so this order won't ship.</span>
        </div>
      )}

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-[1fr_320px]">
        <div className="flex min-w-0 flex-col gap-4">
          <DeliveryTimeline order={order} />
          <Blueprint as="section" aria-labelledby="items-heading" className="bg-card p-4 md:p-6">
            <h2 id="items-heading" className="h mb-3 text-[24px]">
              Items
            </h2>
            {order.items.map((item) => (
              <OrderItemRow key={item.id} item={item} product={products.get(item.productId)} />
            ))}
          </Blueprint>
        </div>
        <OrderSummary order={order} />
      </div>
    </>
  )
}

export default function OrderDetails() {
  const { id = '' } = useParams<{ id: string }>()
  const [retryTick, setRetryTick] = useState(0)
  const [result, setResult] = useState<LoadResult | null>(null)
  const isValidId = /^\d+$/.test(id)
  const requestKey = `${id}:${retryTick}`

  useEffect(() => {
    if (!isValidId) return
    let cancelled = false
    ordersApi
      .getById(Number(id))
      .then((order) => {
        if (!cancelled) setResult({ forKey: requestKey, kind: 'ok', order })
      })
      .catch((error: unknown) => {
        if (cancelled) return
        let kind: LoadKind = 'error'
        if (error instanceof ApiRequestError) {
          if (error.status === 404 || error.status === 400) kind = 'not-found'
          else if (error.status === 403) kind = 'forbidden'
        }
        setResult({ forKey: requestKey, kind, order: null })
      })
    return () => {
      cancelled = true
    }
  }, [id, isValidId, requestKey])

  // Loading is derived: there is no result yet for the current id + retry attempt.
  const current = result?.forKey === requestKey ? result : null
  const kind: LoadKind | 'loading' = !isValidId ? 'not-found' : (current?.kind ?? 'loading')

  let content: ReactNode
  if (kind === 'loading') {
    content = (
      <p role="status" className="text-[16.5px] text-paper-700">
        Loading your order…
      </p>
    )
  } else if (kind === 'not-found') {
    content = (
      <>
        <h1>Order not found</h1>
        <p className="mb-3 text-[16.5px] text-paper-700">We couldn't find an order with that number.</p>
        <BackToOrders />
      </>
    )
  } else if (kind === 'forbidden') {
    content = (
      <>
        <h1>You can't view this order</h1>
        <p className="mb-3 text-[16.5px] text-paper-700">This order belongs to another account.</p>
        <BackToOrders />
      </>
    )
  } else if (kind === 'error' || !current?.order) {
    content = (
      <div role="alert" className="callout-alert flex-wrap">
        <span>We couldn't load this order.</span>
        <Button variant="secondary" onClick={() => setRetryTick((tick) => tick + 1)}>
          Try again
        </Button>
      </div>
    )
  } else {
    content = <LoadedOrder order={current.order} />
  }

  return <div className={PAGE_CLASS}>{content}</div>
}
