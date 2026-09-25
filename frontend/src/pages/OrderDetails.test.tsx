import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    ordersApi: {
      ...actual.ordersApi,
      getById: vi.fn(),
    },
    catalogApi: {
      ...actual.catalogApi,
      getById: vi.fn(),
    },
    usersApi: {
      ...actual.usersApi,
      login: vi.fn(),
    },
    // CartProvider fetches the cart whenever someone is signed in; keep that off the network.
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
      addItem: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { ApiRequestError, cartApi, catalogApi, ordersApi, usersApi, type Order, type Product } from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import RequireAuth from '../components/auth/RequireAuth'
import { formatOrderDate, formatStepDate } from '../lib/orderStatus'
import OrderDetails from './OrderDetails'
import SignIn from './SignIn'

const mockedOrdersApi = vi.mocked(ordersApi)
const mockedCatalogApi = vi.mocked(catalogApi)
const mockedUsersApi = vi.mocked(usersApi)
const mockedCartApi = vi.mocked(cartApi)

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

const EMPTY_CART = { userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 }

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

function makeOrder(overrides: Partial<Order> = {}): Order {
  return {
    id: 42,
    buyerId: 1,
    status: 'PAID',
    fulfillmentStatus: 'SHIPPED',
    totalAmount: 27.5,
    items: [{ id: 100, productId: 1, sellerId: 2, quantity: 2, unitPrice: 10 }],
    address: { fullName: 'Test Buyer', street: '1 Main St', city: 'Seattle', state: 'WA', zip: '98104' },
    shippingMethod: 'STANDARD',
    paymentMethod: 'CARD',
    createdAt: '2026-08-12T15:00:00Z',
    shippedAt: '2026-08-14T15:00:00Z',
    outForDeliveryAt: null,
    deliveredAt: null,
    estimatedDeliveryDate: '2026-08-19',
    ...overrides,
  }
}

function renderOrderDetails(route = '/orders/42') {
  return renderWithProviders(
    <Routes>
      <Route
        path="/orders/:id"
        element={
          <RequireAuth>
            <OrderDetails />
          </RequireAuth>
        }
      />
      <Route path="/signin" element={<SignIn />} />
      <Route path="/orders" element={<div>Orders stub</div>} />
      <Route path="/cart" element={<div>Cart stub</div>} />
    </Routes>,
    { route },
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mockedCartApi.get.mockResolvedValue(EMPTY_CART)
  mockedCatalogApi.getById.mockResolvedValue(makeProduct())
})

describe('OrderDetails page', () => {
  it('shows a loading state, then the full order', async () => {
    seedAuth()
    mockedOrdersApi.getById.mockResolvedValue(makeOrder())

    renderOrderDetails()

    expect(screen.getByRole('status')).toHaveTextContent('Loading your order')
    expect(await screen.findByRole('heading', { name: 'Order #42' })).toBeInTheDocument()
    expect(mockedOrdersApi.getById).toHaveBeenCalledWith(42)

    expect(screen.getByText(`Placed ${formatOrderDate('2026-08-12T15:00:00Z')}`, { exact: false })).toBeInTheDocument()
    expect(screen.getByText('Paid')).toBeInTheDocument()
    expect(screen.getAllByText('Shipped').length).toBeGreaterThan(0)

    const timeline = screen.getByRole('list')
    expect(within(timeline).getByText(formatStepDate(new Date('2026-08-12T15:00:00Z')))).toBeInTheDocument()
    expect(within(timeline).getByText(formatStepDate(new Date('2026-08-14T15:00:00Z')))).toBeInTheDocument()
    expect(within(timeline).getByText(`Estimated ${formatStepDate(new Date(2026, 7, 19))}`)).toBeInTheDocument()
    expect(timeline.querySelector('[aria-current="step"]')).toHaveTextContent('Shipped')

    const productLink = await screen.findByRole('link', { name: 'Widget' })
    expect(productLink).toHaveAttribute('href', '/product/1')
    expect(screen.getByText('Qty 2 ·', { exact: false })).toBeInTheDocument()

    const summary = screen.getByRole('complementary', { name: 'Order summary' })
    expect(within(summary).getByText('1 Main St, Seattle WA 98104')).toBeInTheDocument()
    expect(within(summary).getByText('Standard — 3 to 5 business days')).toBeInTheDocument()
    expect(within(summary).getByText('Credit card ending in 4417')).toBeInTheDocument()
    expect(within(summary).getByText('$20.00')).toBeInTheDocument()
    expect(within(summary).getByText('$27.50')).toBeInTheDocument()
  })

  it('flags a failed payment and hides the delivery estimate', async () => {
    seedAuth()
    mockedOrdersApi.getById.mockResolvedValue(
      makeOrder({ status: 'FAILED', fulfillmentStatus: 'NOT_SHIPPED', shippedAt: null }),
    )

    renderOrderDetails()

    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't charge your payment method")
    expect(screen.getByRole('heading', { name: 'Payment failed' })).toBeInTheDocument()
    expect(screen.queryByText(/Estimated/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Arriving/)).not.toBeInTheDocument()
    expect(screen.queryByText('Preparing for shipment')).not.toBeInTheDocument()
  })

  it('shows "Order not found" for a 404', async () => {
    seedAuth()
    mockedOrdersApi.getById.mockRejectedValue(new ApiRequestError(404))

    renderOrderDetails()

    expect(await screen.findByRole('heading', { name: 'Order not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Back to your orders/ })).toHaveAttribute('href', '/orders')
  })

  it("explains that another account's order can't be viewed on a 403", async () => {
    seedAuth()
    mockedOrdersApi.getById.mockRejectedValue(new ApiRequestError(403, 'Forbidden'))

    renderOrderDetails()

    expect(await screen.findByRole('heading', { name: "You can't view this order" })).toBeInTheDocument()
    expect(screen.getByText('This order belongs to another account.')).toBeInTheDocument()
  })

  it('treats a non-numeric id as not found without calling the API', () => {
    seedAuth()

    renderOrderDetails('/orders/abc')

    expect(screen.getByRole('heading', { name: 'Order not found' })).toBeInTheDocument()
    expect(mockedOrdersApi.getById).not.toHaveBeenCalled()
  })

  it('shows an error with a retry for unexpected failures', async () => {
    seedAuth()
    mockedOrdersApi.getById.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(makeOrder())

    renderOrderDetails()

    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't load this order.")
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'Order #42' })).toBeInTheDocument()
    expect(mockedOrdersApi.getById).toHaveBeenCalledTimes(2)
  })

  it('sends signed-out visitors to sign in and brings them back to the order', async () => {
    mockedUsersApi.login.mockResolvedValue({
      id: AUTH_USER.id,
      name: AUTH_USER.name,
      email: AUTH_USER.email,
      role: AUTH_USER.role,
      token: 'test-token',
      expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    })
    mockedOrdersApi.getById.mockResolvedValue(makeOrder())

    renderOrderDetails()

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(mockedOrdersApi.getById).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'buyer@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByText('Continue'))

    expect(await screen.findByRole('heading', { name: 'Order #42' })).toBeInTheDocument()
    expect(mockedOrdersApi.getById).toHaveBeenCalledWith(42)
  })

  it('adds the item to the cart and opens it on "Buy it again"', async () => {
    seedAuth()
    mockedOrdersApi.getById.mockResolvedValue(makeOrder())
    mockedCartApi.addItem.mockResolvedValue(EMPTY_CART)

    renderOrderDetails()

    const buyAgain = await screen.findByRole('button', { name: 'Buy it again' })
    await waitFor(() => expect(buyAgain).toBeEnabled())
    fireEvent.click(buyAgain)

    await waitFor(() => expect(mockedCartApi.addItem).toHaveBeenCalledWith(AUTH_USER.id, 1, 2))
    expect(await screen.findByText('Cart stub')).toBeInTheDocument()
  })
})
