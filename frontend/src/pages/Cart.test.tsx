import { screen, waitFor, within } from '@testing-library/react'
import { vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import type { CartView, Product } from '../services/api'
import Cart from './Cart'

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
  }
})

import { cartApi, catalogApi } from '../services/api'

const mockedCartApi = vi.mocked(cartApi)
const mockedCatalogApi = vi.mocked(catalogApi)

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

function renderCart() {
  return renderWithProviders(
    <Routes>
      <Route path="/cart" element={<Cart />} />
      <Route path="/checkout" element={<div>Checkout stub</div>} />
    </Routes>,
    { route: '/cart' },
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

describe('Cart page', () => {
  it('renders cart items and navigates to checkout on proceed', async () => {
    seedAuth()
    mockedCartApi.get.mockResolvedValue(
      makeCartView({
        items: [
          { productId: 1, productName: 'Widget', unitPrice: 10, quantity: 2, lineTotal: 20, savedForLater: false },
        ],
        itemCount: 2,
        total: 20,
      }),
    )
    mockedCartApi.addItem.mockResolvedValue(
      makeCartView({
        items: [
          { productId: 1, productName: 'Widget', unitPrice: 10, quantity: 3, lineTotal: 30, savedForLater: false },
        ],
        itemCount: 3,
        total: 30,
      }),
    )
    mockedCatalogApi.getById.mockResolvedValue(makeProduct())

    renderCart()

    await waitFor(() => expect(screen.getAllByText('Widget').length).toBeGreaterThan(0))
    const decreaseButton = screen.getByLabelText('Decrease quantity')
    const qtyContainer = decreaseButton.parentElement as HTMLElement
    expect(within(qtyContainer).getByText('2')).toBeInTheDocument()
    expect(screen.getAllByText('$20.00').length).toBeGreaterThan(0)

    screen.getByLabelText('Increase quantity').click()
    await waitFor(() => expect(mockedCartApi.addItem).toHaveBeenCalledWith(AUTH_USER.id, 1, 1))

    screen.getByText('Proceed to checkout').click()
    await waitFor(() => expect(screen.getByText('Checkout stub')).toBeInTheDocument())
  })

  it('shows an empty-cart message and disables checkout when the cart has no items', async () => {
    seedAuth()
    mockedCartApi.get.mockResolvedValue(makeCartView({ items: [] }))

    renderCart()

    await waitFor(() => expect(screen.getByText('Your cart is empty')).toBeInTheDocument())
    expect(screen.getByText('Proceed to checkout')).toBeDisabled()
  })
})
