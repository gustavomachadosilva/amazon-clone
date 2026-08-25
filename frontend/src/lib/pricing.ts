import { usd } from './format'

export const FREE_SHIPPING_MARKETING_THRESHOLD = 49

export interface CheckoutTotals {
  shipping: number
  discount: number
  tax: number
  total: number
}

export function computeCheckoutTotals(
  subtotal: number,
  shippingMethod: 'standard' | 'express' | 'pickup',
): CheckoutTotals {
  const shipping = shippingMethod === 'express' ? 9.99 : 0
  const discount = subtotal > 150 ? subtotal * 0.05 : 0
  const tax = (subtotal - discount) * 0.089
  const total = subtotal + shipping + tax - discount
  return { shipping, discount, tax, total }
}

export function freeShippingMessage(subtotal: number): string {
  if (subtotal >= FREE_SHIPPING_MARKETING_THRESHOLD) return 'Your order qualifies for FREE shipping.'
  return `${usd(FREE_SHIPPING_MARKETING_THRESHOLD - subtotal)} away from FREE shipping.`
}

export function installmentLine(price: number): string {
  return `or 4 interest-free payments of ${usd(price / 4)}`
}
