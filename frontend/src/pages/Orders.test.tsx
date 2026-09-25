import { fireEvent, screen } from '@testing-library/react'
import { vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    ordersApi: {
      ...actual.ordersApi,
      listByBuyer: vi.fn(),
    },
    catalogApi: {
      ...actual.catalogApi,
      getById: vi.fn(),
    },
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
  }
})

import { cartApi, catalogApi, ordersApi, type Order } from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import { formatDeliveryDate } from '../lib/deliveryDate'
import Orders from './Orders'

const mockedOrdersApi = vi.mocked(ordersApi)
const mockedCatalogApi = vi.mocked(catalogApi)
const mockedCartApi = vi.mocked(cartApi)

function seedAuth() {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      id: 1,
      name: 'Test Buyer',
      email: 'buyer@example.com',
      role: 'BUYER',
      token: 'test-token',
      tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }),
  )
}

function makeOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: 7,
    buyerId: 1,
    status: 'PAID',
    fulfillmentStatus: 'NOT_SHIPPED',
    totalAmount: 10,
    items: [{ id: 70, productId: 1, sellerId: 2, quantity: 1, unitPrice: 10 }],
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

function renderOrders() {
  return renderWithProviders(
    <Routes>
      <Route path="/orders" element={<Orders />} />
      <Route path="/orders/:id" element={<div>Order details stub</div>} />
    </Routes>,
    { route: '/orders' },
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mockedCartApi.get.mockResolvedValue({ userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 })
  mockedCatalogApi.getById.mockRejectedValue(new Error('not needed'))
})

describe('Orders page', () => {
  it('shows "Payment failed" instead of an arrival date for failed orders', async () => {
    seedAuth()
    mockedOrdersApi.listByBuyer.mockResolvedValue([makeOrder({ status: 'FAILED' })])

    renderOrders()

    expect(await screen.findByText('Payment failed')).toBeInTheDocument()
    expect(screen.queryByText(/Arriving/)).not.toBeInTheDocument()
  })

  it("uses the backend's estimated delivery date for paid orders", async () => {
    seedAuth()
    mockedOrdersApi.listByBuyer.mockResolvedValue([makeOrder()])

    renderOrders()

    expect(await screen.findByText(`Arriving ${formatDeliveryDate(new Date(2026, 7, 19))}`)).toBeInTheDocument()
  })

  it('opens the order details from the order number', async () => {
    seedAuth()
    mockedOrdersApi.listByBuyer.mockResolvedValue([makeOrder()])

    renderOrders()

    const orderLink = await screen.findByRole('link', { name: 'Order #7' })
    expect(orderLink).toHaveAttribute('href', '/orders/7')
    fireEvent.click(orderLink)

    expect(await screen.findByText('Order details stub')).toBeInTheDocument()
  })

  it('opens the order details from "View order details"', async () => {
    seedAuth()
    mockedOrdersApi.listByBuyer.mockResolvedValue([makeOrder()])

    renderOrders()

    fireEvent.click(await screen.findByRole('link', { name: /View order details/ }))

    expect(await screen.findByText('Order details stub')).toBeInTheDocument()
  })
})
