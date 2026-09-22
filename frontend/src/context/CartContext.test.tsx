import { render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { AuthProvider } from './AuthContext'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import type { CartView, Product } from '../services/api'

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
  }
})

// Imported after the mock so it picks up the mocked module.
import { cartApi } from '../services/api'
import { CartProvider, useCart } from './CartContext'

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

function Consumer() {
  const cart = useCart()
  return (
    <div>
      <div data-testid="item-count">{cart.itemCount}</div>
      <ul>
        {cart.items.map((line) => (
          <li key={line.productId} data-testid={`line-${line.productId}`}>
            {line.name} x{line.qty}
          </li>
        ))}
      </ul>
      <button onClick={() => cart.decrementQty(1)}>decrement</button>
      <button onClick={() => cart.addItem({ id: 1 } as unknown as Product)}>add</button>
    </div>
  )
}

function renderConsumer() {
  return render(
    <AuthProvider>
      <CartProvider>
        <Consumer />
      </CartProvider>
    </AuthProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

describe('CartContext', () => {
  it('chains decrementQty mutations off the previous resolved value, not stale state', async () => {
    seedAuth()
    mockedCartApi.get.mockResolvedValue(
      makeCartView({
        items: [
          {
            productId: 1,
            productName: 'Widget',
            unitPrice: 10,
            quantity: 3,
            lineTotal: 30,
            savedForLater: false,
          },
        ],
        itemCount: 3,
        total: 30,
      }),
    )
    mockedCartApi.updateQuantity.mockImplementation((_userId, productId, quantity) =>
      Promise.resolve(
        makeCartView({
          items: [
            {
              productId,
              productName: 'Widget',
              unitPrice: 10,
              quantity,
              lineTotal: 10 * quantity,
              savedForLater: false,
            },
          ],
          itemCount: quantity,
          total: 10 * quantity,
        }),
      ),
    )

    renderConsumer()

    await waitFor(() => expect(screen.getByTestId('line-1')).toHaveTextContent('Widget x3'))

    const decrementButton = screen.getByText('decrement')
    // Fire both decrements back-to-back without awaiting in between.
    decrementButton.click()
    decrementButton.click()

    await waitFor(() => expect(mockedCartApi.updateQuantity).toHaveBeenCalledTimes(2))

    expect(mockedCartApi.updateQuantity).toHaveBeenNthCalledWith(1, AUTH_USER.id, 1, 2)
    expect(mockedCartApi.updateQuantity).toHaveBeenNthCalledWith(2, AUTH_USER.id, 1, 1)

    await waitFor(() => expect(screen.getByTestId('line-1')).toHaveTextContent('Widget x1'))
  })

  it('keeps the mutation queue alive after a rejected mutation and still applies the next one', async () => {
    seedAuth()
    mockedCartApi.get.mockResolvedValue(makeCartView())

    mockedCartApi.addItem
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce(
        makeCartView({
          items: [
            {
              productId: 1,
              productName: 'Widget',
              unitPrice: 10,
              quantity: 1,
              lineTotal: 10,
              savedForLater: false,
            },
          ],
          itemCount: 1,
          total: 10,
        }),
      )

    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    renderConsumer()

    await waitFor(() => expect(screen.getByTestId('item-count')).toHaveTextContent('0'))

    const addButton = screen.getByText('add')
    addButton.click()
    addButton.click()

    await waitFor(() => expect(mockedCartApi.addItem).toHaveBeenCalledTimes(2))

    // The rejected first mutation must not clobber the view, and the second
    // (successful) queued mutation must still resolve and update it.
    await waitFor(() => expect(screen.getByTestId('line-1')).toHaveTextContent('Widget x1'))
    expect(screen.getByTestId('item-count')).toHaveTextContent('1')

    consoleErrorSpy.mockRestore()
  })
})
