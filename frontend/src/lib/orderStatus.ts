import type { FulfillmentStatus, Order, OrderStatus } from '../services/api'
import { formatDeliveryDate, getStandardDeliveryLabel } from './deliveryDate'

type OrderStatusFields = Pick<
  Order,
  'status' | 'fulfillmentStatus' | 'estimatedDeliveryDate' | 'createdAt' | 'shippedAt' | 'outForDeliveryAt' | 'deliveredAt'
>

export interface StatusBadge {
  label: string
  className: string
}

export type HeadlineTone = 'accent' | 'alert' | 'neutral'

/** Text colour for a delivery headline, shared by the orders list and the order details page. */
export const HEADLINE_TONE_CLASS: Record<HeadlineTone, string> = {
  accent: 'text-accent-700',
  alert: 'text-alert-700',
  neutral: 'text-paper-800',
}

export interface DeliveryHeadline {
  text: string
  tone: HeadlineTone
}

export type TimelineStepState = 'done' | 'current' | 'upcoming'

export interface TimelineStep {
  key: FulfillmentStatus | 'ORDERED'
  label: string
  // Formatted date of a completed step, or the estimate on the final step; null otherwise.
  date: string | null
  // True when `date` is an estimate rather than the moment the step happened.
  estimated: boolean
  state: TimelineStepState
}

/**
 * Parses an ISO calendar date ("YYYY-MM-DD") as a local date. `new Date("2026-08-13")` would be
 * read as UTC midnight and show the previous day in timezones west of UTC.
 */
export function parseLocalDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** "August 12, 2026" — the style used for order placement dates. */
export function formatOrderDate(iso: string): string {
  return new Date(iso).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })
}

/** "Aug 12" — compact date for timeline steps. */
export function formatStepDate(date: Date): string {
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

export function paymentBadge(status: OrderStatus): StatusBadge {
  switch (status) {
    case 'PAID':
      return { label: 'Paid', className: 'tag tag-accent' }
    case 'FAILED':
      return { label: 'Payment failed', className: 'tag tag-alert' }
    case 'CANCELLED':
      return { label: 'Cancelled', className: 'tag tag-neutral' }
    case 'PENDING':
    case 'PROCESSING':
      return { label: 'Payment processing', className: 'tag tag-neutral' }
  }
}

/** Shipment progress only means something once the order is paid. */
export function shipmentBadge(order: Pick<Order, 'status' | 'fulfillmentStatus'>): StatusBadge | null {
  if (order.status !== 'PAID') return null
  switch (order.fulfillmentStatus) {
    case 'NOT_SHIPPED':
      return { label: 'Preparing for shipment', className: 'tag tag-accent-2' }
    case 'SHIPPED':
      return { label: 'Shipped', className: 'tag tag-accent' }
    case 'OUT_FOR_DELIVERY':
      return { label: 'Out for delivery', className: 'tag tag-accent' }
    case 'DELIVERED':
      return { label: 'Delivered', className: 'tag tag-neutral' }
  }
}

/** The delivery address can change only until the order ships, and never once it is cancelled. */
export function canChangeAddress(order: Pick<Order, 'status' | 'fulfillmentStatus'>): boolean {
  return order.fulfillmentStatus === 'NOT_SHIPPED' && order.status !== 'CANCELLED'
}

/**
 * The backend computes an estimate for every order, including failed ones, so the UI decides
 * when it is meaningful: only for paid orders that haven't been delivered yet.
 */
export function showsDeliveryEstimate(order: Pick<Order, 'status' | 'fulfillmentStatus' | 'estimatedDeliveryDate'>): boolean {
  return order.status === 'PAID' && order.fulfillmentStatus !== 'DELIVERED' && order.estimatedDeliveryDate != null
}

function estimateLabel(order: OrderStatusFields): string {
  return order.estimatedDeliveryDate
    ? formatDeliveryDate(parseLocalDate(order.estimatedDeliveryDate))
    : // Legacy orders without an estimate fall back to the standard window from the order date.
      getStandardDeliveryLabel(new Date(order.createdAt))
}

export function deliveryHeadline(order: OrderStatusFields): DeliveryHeadline {
  switch (order.status) {
    case 'FAILED':
      return { text: 'Payment failed', tone: 'alert' }
    case 'CANCELLED':
      return { text: 'Order cancelled', tone: 'neutral' }
    case 'PENDING':
    case 'PROCESSING':
      return { text: 'Payment processing', tone: 'neutral' }
    case 'PAID':
      break
  }
  switch (order.fulfillmentStatus) {
    case 'DELIVERED':
      return {
        text: order.deliveredAt ? `Delivered ${formatDeliveryDate(new Date(order.deliveredAt))}` : 'Delivered',
        tone: 'accent',
      }
    case 'OUT_FOR_DELIVERY':
      return { text: 'Out for delivery', tone: 'accent' }
    case 'SHIPPED':
      return { text: `Shipped — arriving ${estimateLabel(order)}`, tone: 'accent' }
    case 'NOT_SHIPPED':
      return { text: `Arriving ${estimateLabel(order)}`, tone: 'accent' }
  }
}

const FULFILLMENT_INDEX: Record<FulfillmentStatus, number> = {
  NOT_SHIPPED: 0,
  SHIPPED: 1,
  OUT_FOR_DELIVERY: 2,
  DELIVERED: 3,
}

export function timelineSteps(order: OrderStatusFields): TimelineStep[] {
  // Unpaid orders never progress past "Ordered", whatever the fulfillment column says.
  const reached = order.status === 'PAID' ? FULFILLMENT_INDEX[order.fulfillmentStatus] : 0
  const steps: { key: TimelineStep['key']; label: string; at: string | null }[] = [
    { key: 'ORDERED', label: 'Ordered', at: order.createdAt },
    { key: 'SHIPPED', label: 'Shipped', at: order.shippedAt },
    { key: 'OUT_FOR_DELIVERY', label: 'Out for delivery', at: order.outForDeliveryAt },
    { key: 'DELIVERED', label: 'Delivered', at: order.deliveredAt },
  ]

  return steps.map((step, index) => {
    const state: TimelineStepState = index < reached ? 'done' : index === reached ? 'current' : 'upcoming'
    if (state !== 'upcoming') {
      return { key: step.key, label: step.label, date: step.at ? formatStepDate(new Date(step.at)) : null, estimated: false, state }
    }
    if (step.key === 'DELIVERED' && showsDeliveryEstimate(order) && order.estimatedDeliveryDate) {
      return {
        key: step.key,
        label: step.label,
        date: formatStepDate(parseLocalDate(order.estimatedDeliveryDate)),
        estimated: true,
        state,
      }
    }
    return { key: step.key, label: step.label, date: null, estimated: false, state }
  })
}
