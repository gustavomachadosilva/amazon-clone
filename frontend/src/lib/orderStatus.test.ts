import { describe, expect, it } from 'vitest'
import type { Order } from '../services/api'
import { formatDeliveryDate, getStandardDeliveryLabel } from './deliveryDate'
import {
  canChangeAddress,
  deliveryHeadline,
  formatStepDate,
  nextFulfillmentAction,
  parseLocalDate,
  paymentBadge,
  shipmentBadge,
  showsDeliveryEstimate,
  timelineSteps,
} from './orderStatus'

function makeOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: 42,
    buyerId: 1,
    status: 'PAID',
    fulfillmentStatus: 'NOT_SHIPPED',
    totalAmount: 25,
    items: [],
    address: null,
    shippingMethod: 'STANDARD',
    paymentMethod: 'CARD',
    createdAt: '2026-08-12T15:00:00Z',
    shippedAt: null,
    outForDeliveryAt: null,
    deliveredAt: null,
    estimatedDeliveryDate: '2026-08-19',
    ...overrides,
  }
}

describe('parseLocalDate', () => {
  it('reads a calendar date in local time, without a UTC day shift', () => {
    const date = parseLocalDate('2026-08-13')
    expect(date.getFullYear()).toBe(2026)
    expect(date.getMonth()).toBe(7)
    expect(date.getDate()).toBe(13)
  })
})

describe('paymentBadge', () => {
  it('maps each payment status to a label and tag class', () => {
    expect(paymentBadge('PAID')).toEqual({ label: 'Paid', className: 'tag tag-accent' })
    expect(paymentBadge('FAILED')).toEqual({ label: 'Payment failed', className: 'tag tag-alert' })
    expect(paymentBadge('PENDING').label).toBe('Payment processing')
    expect(paymentBadge('PROCESSING').label).toBe('Payment processing')
    expect(paymentBadge('CANCELLED').label).toBe('Cancelled')
  })
})

describe('shipmentBadge', () => {
  it('is only shown for paid orders', () => {
    expect(shipmentBadge(makeOrder({ status: 'FAILED' }))).toBeNull()
    expect(shipmentBadge(makeOrder({ status: 'PENDING' }))).toBeNull()
    expect(shipmentBadge(makeOrder({ fulfillmentStatus: 'NOT_SHIPPED' }))?.label).toBe('Preparing for shipment')
    expect(shipmentBadge(makeOrder({ fulfillmentStatus: 'SHIPPED' }))?.label).toBe('Shipped')
    expect(shipmentBadge(makeOrder({ fulfillmentStatus: 'OUT_FOR_DELIVERY' }))?.label).toBe('Out for delivery')
    expect(shipmentBadge(makeOrder({ fulfillmentStatus: 'DELIVERED' }))?.label).toBe('Delivered')
  })
})

describe('nextFulfillmentAction', () => {
  it('offers the immediate next shipping step for paid orders', () => {
    expect(nextFulfillmentAction({ status: 'PAID', fulfillmentStatus: 'NOT_SHIPPED' })).toEqual({
      next: 'SHIPPED',
      label: 'Mark as shipped',
    })
    expect(nextFulfillmentAction({ status: 'PAID', fulfillmentStatus: 'SHIPPED' })).toEqual({
      next: 'OUT_FOR_DELIVERY',
      label: 'Mark as out for delivery',
    })
    expect(nextFulfillmentAction({ status: 'PAID', fulfillmentStatus: 'OUT_FOR_DELIVERY' })).toEqual({
      next: 'DELIVERED',
      label: 'Mark as delivered',
    })
  })

  it('has nothing to offer once delivered', () => {
    expect(nextFulfillmentAction({ status: 'PAID', fulfillmentStatus: 'DELIVERED' })).toBeNull()
  })

  it('has nothing to offer for unpaid orders', () => {
    expect(nextFulfillmentAction({ status: 'PENDING', fulfillmentStatus: 'NOT_SHIPPED' })).toBeNull()
    expect(nextFulfillmentAction({ status: 'FAILED', fulfillmentStatus: 'NOT_SHIPPED' })).toBeNull()
    expect(nextFulfillmentAction({ status: 'CANCELLED', fulfillmentStatus: 'NOT_SHIPPED' })).toBeNull()
  })
})

describe('canChangeAddress', () => {
  it('allows changes only before shipment and never for cancelled orders', () => {
    expect(canChangeAddress(makeOrder())).toBe(true)
    expect(canChangeAddress(makeOrder({ status: 'PENDING' }))).toBe(true)
    expect(canChangeAddress(makeOrder({ status: 'FAILED' }))).toBe(true)
    expect(canChangeAddress(makeOrder({ status: 'CANCELLED' }))).toBe(false)
    expect(canChangeAddress(makeOrder({ fulfillmentStatus: 'SHIPPED' }))).toBe(false)
    expect(canChangeAddress(makeOrder({ fulfillmentStatus: 'OUT_FOR_DELIVERY' }))).toBe(false)
    expect(canChangeAddress(makeOrder({ fulfillmentStatus: 'DELIVERED' }))).toBe(false)
  })
})

describe('showsDeliveryEstimate', () => {
  it('shows the estimate only for paid, undelivered orders that have one', () => {
    expect(showsDeliveryEstimate(makeOrder())).toBe(true)
    expect(showsDeliveryEstimate(makeOrder({ fulfillmentStatus: 'SHIPPED' }))).toBe(true)
    expect(showsDeliveryEstimate(makeOrder({ fulfillmentStatus: 'DELIVERED' }))).toBe(false)
    expect(showsDeliveryEstimate(makeOrder({ status: 'FAILED' }))).toBe(false)
    expect(showsDeliveryEstimate(makeOrder({ status: 'PROCESSING' }))).toBe(false)
    expect(showsDeliveryEstimate(makeOrder({ status: 'CANCELLED' }))).toBe(false)
    expect(showsDeliveryEstimate(makeOrder({ estimatedDeliveryDate: null }))).toBe(false)
  })
})

describe('deliveryHeadline', () => {
  it('uses the real payment status instead of an arrival date for unpaid orders', () => {
    expect(deliveryHeadline(makeOrder({ status: 'FAILED' }))).toEqual({ text: 'Payment failed', tone: 'alert' })
    expect(deliveryHeadline(makeOrder({ status: 'CANCELLED' }))).toEqual({ text: 'Order cancelled', tone: 'neutral' })
    expect(deliveryHeadline(makeOrder({ status: 'PENDING' }))).toEqual({ text: 'Payment processing', tone: 'neutral' })
  })

  it('uses the backend estimate for paid orders', () => {
    const expected = formatDeliveryDate(new Date(2026, 7, 19))
    expect(deliveryHeadline(makeOrder()).text).toBe(`Arriving ${expected}`)
    expect(deliveryHeadline(makeOrder({ fulfillmentStatus: 'SHIPPED' })).text).toBe(`Shipped — arriving ${expected}`)
    expect(deliveryHeadline(makeOrder({ fulfillmentStatus: 'OUT_FOR_DELIVERY' })).text).toBe('Out for delivery')
  })

  it('reports the delivery date once delivered', () => {
    const deliveredAt = '2026-08-18T12:00:00Z'
    const headline = deliveryHeadline(makeOrder({ fulfillmentStatus: 'DELIVERED', deliveredAt }))
    expect(headline).toEqual({ text: `Delivered ${formatDeliveryDate(new Date(deliveredAt))}`, tone: 'accent' })
  })

  it('falls back to the standard window for legacy orders without an estimate', () => {
    const order = makeOrder({ estimatedDeliveryDate: null })
    expect(deliveryHeadline(order).text).toBe(`Arriving ${getStandardDeliveryLabel(new Date(order.createdAt))}`)
  })
})

describe('timelineSteps', () => {
  it('marks steps up to the fulfillment status as reached and estimates delivery', () => {
    const shippedAt = '2026-08-14T12:00:00Z'
    const steps = timelineSteps(makeOrder({ fulfillmentStatus: 'SHIPPED', shippedAt }))
    expect(steps.map((step) => step.state)).toEqual(['done', 'current', 'upcoming', 'upcoming'])
    expect(steps[0].date).toBe(formatStepDate(new Date('2026-08-12T15:00:00Z')))
    expect(steps[1].date).toBe(formatStepDate(new Date(shippedAt)))
    expect(steps[2].date).toBeNull()
    expect(steps[3]).toMatchObject({ date: formatStepDate(new Date(2026, 7, 19)), estimated: true })
  })

  it('marks every step reached once delivered, with no estimate', () => {
    const steps = timelineSteps(
      makeOrder({
        fulfillmentStatus: 'DELIVERED',
        shippedAt: '2026-08-14T12:00:00Z',
        outForDeliveryAt: '2026-08-17T08:00:00Z',
        deliveredAt: '2026-08-17T16:00:00Z',
      }),
    )
    expect(steps.map((step) => step.state)).toEqual(['done', 'done', 'done', 'current'])
    expect(steps.every((step) => !step.estimated && step.date !== null)).toBe(true)
  })

  it('stops at "Ordered" without an estimate for failed orders', () => {
    const steps = timelineSteps(makeOrder({ status: 'FAILED' }))
    expect(steps.map((step) => step.state)).toEqual(['current', 'upcoming', 'upcoming', 'upcoming'])
    expect(steps.slice(1).every((step) => step.date === null)).toBe(true)
  })
})
