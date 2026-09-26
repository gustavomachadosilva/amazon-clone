import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { CartProvider } from '../context/CartContext'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import { computeCheckoutTotals } from '../lib/pricing'
import { usd } from '../lib/format'
import { DEFAULT_ADDRESS } from '../lib/constants'
import type { CartView, Order, Product } from '../services/api'
import Checkout from './Checkout'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    cartApi: {
      get: vi.fn(),
      addItem: vi.fn(),
      updateQuantity: vi.fn(),
      removeItem: vi.fn(),
      saveForLater: vi.fn(),
      moveToCart: vi.fn(),
      clear: vi.fn(),
    },
    catalogApi: {
      ...actual.catalogApi,
      getById: vi.fn(),
    },
    ordersApi: {
      ...actual.ordersApi,
      checkout: vi.fn(),
    },
  }
})

import { cartApi, catalogApi, ordersApi } from '../services/api'

const mockedCartApi = vi.mocked(cartApi)
const mockedCatalogApi = vi.mocked(catalogApi)
const mockedOrdersApi = vi.mocked(ordersApi)

const AUTH_USER = {
  id: 1,
  name: 'Test Buyer',
  email: 'buyer@example.com',
  role: 'BUYER' as const,
  token: 'test-token',
  tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
}

function seedAuth() {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(AUTH_USER))
}

function makeProduct(overrides: Partial<Product> = {}): Product {
  return {
    id: 1,
    name: 'Widget',
    description: 'A fine widget',
    price: 10,
    stockQuantity: 5,
    category: 'Gadgets',
    sellerId: 2,
    brand: null,
    warrantyMonths: null,
    modelNumber: null,
    listPrice: null,
    averageRating: 4,
    reviewCount: 3,
    ...overrides,
  }
}

function makeCartView(overrides: Partial<CartView> = {}): CartView {
  return {
    userId: AUTH_USER.id,
    items: [],
    savedForLater: [],
    itemCount: 0,
    total: 0,
    ...overrides,
  }
}

function makeOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: 123,
    buyerId: AUTH_USER.id,
    status: 'PAID',
    totalAmount: 20,
    items: [],
    address: null,
    shippingMethod: null,
    paymentMethod: null,
    createdAt: new Date().toISOString(),
    fulfillmentStatus: 'NOT_SHIPPED',
    shippedAt: null,
    outForDeliveryAt: null,
    deliveredAt: null,
    estimatedDeliveryDate: null,
    ...overrides,
  }
}

function renderCheckout() {
  return render(
    <MemoryRouter initialEntries={['/checkout']}>
      <AuthProvider>
        <CartProvider>
          <Routes>
            <Route path="/checkout" element={<Checkout />} />
            <Route path="/order/:id" element={<div>Order confirmation stub</div>} />
            <Route path="/signin" element={<div>Sign in stub</div>} />
          </Routes>
        </CartProvider>
      </AuthProvider>
    </MemoryRouter>,
  )
}

const CART_ITEM = { productId: 1, productName: 'Widget', unitPrice: 10, quantity: 2, lineTotal: 20, savedForLater: false }

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

describe('Checkout page', () => {
  it('computes totals correctly, places the order, clears the cart, and navigates on success', async () => {
    seedAuth()
    mockedCartApi.get.mockResolvedValue(makeCartView({ items: [CART_ITEM], itemCount: 2, total: 20 }))
    mockedCartApi.clear.mockResolvedValue(makeCartView())
    mockedCatalogApi.getById.mockResolvedValue(makeProduct())
    mockedOrdersApi.checkout.mockResolvedValue(makeOrder({ status: 'PAID', id: 123 }))

    renderCheckout()

    await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))

    const totals = computeCheckoutTotals(20, 'STANDARD')
    expect(screen.getByText(usd(totals.total))).toBeInTheDocument()
    expect(screen.getByText(usd(totals.tax))).toBeInTheDocument()

    screen.getByText('Place your order').click()

    await waitFor(() => expect(mockedOrdersApi.checkout).toHaveBeenCalledTimes(1))

    const [payload, idempotencyKey] = mockedOrdersApi.checkout.mock.calls[0]
    expect(payload).toEqual({
      items: [{ productId: 1, quantity: 2 }],
      address: {
        fullName: AUTH_USER.name,
        zip: DEFAULT_ADDRESS.zip,
        street: DEFAULT_ADDRESS.street,
        city: DEFAULT_ADDRESS.city,
        state: DEFAULT_ADDRESS.state,
      },
      shippingMethod: 'STANDARD',
      paymentMethod: 'CARD',
    })
    expect(idempotencyKey).toBeTruthy()
    expect(typeof idempotencyKey).toBe('string')

    await waitFor(() => expect(mockedCartApi.clear).toHaveBeenCalledWith(AUTH_USER.id))
    await waitFor(() => expect(screen.getByText('Order confirmation stub')).toBeInTheDocument())
  })

  it('shows a declined-payment error without clearing the cart, and reuses the same idempotency key on retry', async () => {
    seedAuth()
    mockedCartApi.get.mockResolvedValue(makeCartView({ items: [CART_ITEM], itemCount: 2, total: 20 }))
    mockedCatalogApi.getById.mockResolvedValue(makeProduct())
    mockedOrdersApi.checkout.mockResolvedValueOnce(makeOrder({ status: 'FAILED', id: 999 }))

    renderCheckout()

    await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))

    screen.getByText('Place your order').click()

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/payment was declined/i),
    )
    expect(mockedCartApi.clear).not.toHaveBeenCalled()
    expect(screen.queryByText('Order confirmation stub')).not.toBeInTheDocument()

    const retryButton = await screen.findByText('Try again')

    mockedOrdersApi.checkout.mockResolvedValueOnce(makeOrder({ status: 'PAID', id: 456 }))
    retryButton.click()

    await waitFor(() => expect(mockedOrdersApi.checkout).toHaveBeenCalledTimes(2))

    const firstKey = mockedOrdersApi.checkout.mock.calls[0][1]
    const secondKey = mockedOrdersApi.checkout.mock.calls[1][1]
    expect(secondKey).toBe(firstKey)

    await waitFor(() => expect(screen.getByText('Order confirmation stub')).toBeInTheDocument())
  })

  it('blocks checkout until the address is complete', async () => {
    seedAuth()
    mockedCartApi.get.mockResolvedValue(makeCartView({ items: [CART_ITEM], itemCount: 2, total: 20 }))
    mockedCatalogApi.getById.mockResolvedValue(makeProduct())

    renderCheckout()

    await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))

    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: '   ' } })
    fireEvent.click(screen.getByText('Place your order'))

    expect(await screen.findByText('Enter a full name.')).toBeInTheDocument()
    expect(screen.getByLabelText('Full name')).toHaveAttribute('aria-invalid', 'true')
    expect(mockedOrdersApi.checkout).not.toHaveBeenCalled()
  })
})
