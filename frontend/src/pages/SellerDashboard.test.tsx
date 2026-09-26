import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { vi } from 'vitest'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    sellersApi: {
      ...actual.sellersApi,
      getInventory: vi.fn(),
      getOrders: vi.fn(),
      getMetrics: vi.fn(),
      advanceFulfillment: vi.fn(),
    },
    catalogApi: {
      ...actual.catalogApi,
      getCategories: vi.fn(),
    },
    // CartProvider fetches the cart whenever someone is signed in; keep that off the network.
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { ApiRequestError, cartApi, catalogApi, sellersApi, type SellerOrder } from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import SellerDashboard from './SellerDashboard'

const mockedSellersApi = vi.mocked(sellersApi)
const mockedCatalogApi = vi.mocked(catalogApi)
const mockedCartApi = vi.mocked(cartApi)

const AUTH_USER = {
  id: 10,
  name: 'Test Seller',
  email: 'seller@example.com',
  role: 'SELLER' as const,
  token: 'test-token',
  tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
}

function makeOrder(overrides: Partial<SellerOrder> = {}): SellerOrder {
  return {
    orderId: 42,
    buyerId: 1,
    status: 'PAID',
    fulfillmentStatus: 'NOT_SHIPPED',
    createdAt: '2026-08-12T15:00:00Z',
    items: [{ productId: 5, quantity: 2, unitPrice: 10 }],
    subtotal: 20,
    ...overrides,
  }
}

async function renderOrdersTab(orders: SellerOrder[]) {
  mockedSellersApi.getOrders.mockResolvedValue(orders)
  renderWithProviders(<SellerDashboard />, { route: '/seller' })
  fireEvent.click(screen.getByRole('tab', { name: /orders/i }))
  await screen.findByText(`#${orders[0].orderId}`)
}

function rowFor(orderId: number): HTMLElement {
  return screen.getByText(`#${orderId}`).closest('tr') as HTMLElement
}

describe('SellerDashboard orders tab', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(AUTH_USER))
    mockedCartApi.get.mockResolvedValue({ userId: 10, items: [], savedForLater: [], itemCount: 0, total: 0 })
    mockedCatalogApi.getCategories.mockResolvedValue([])
    mockedSellersApi.getInventory.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 10,
    } as unknown as Awaited<ReturnType<typeof sellersApi.getInventory>>)
    mockedSellersApi.getMetrics.mockResolvedValue({ totalRevenue: 0, lowStockProducts: [] })
  })

  it('shows the shipping status and the next step for a paid order', async () => {
    await renderOrdersTab([makeOrder()])

    const row = rowFor(42)
    expect(within(row).getByText('Preparing for shipment')).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: 'Mark as shipped' })).toBeInTheDocument()
  })

  it('offers no shipping action for unpaid or delivered orders', async () => {
    await renderOrdersTab([
      makeOrder({ orderId: 1, status: 'PENDING' }),
      makeOrder({ orderId: 2, fulfillmentStatus: 'DELIVERED' }),
    ])

    expect(within(rowFor(1)).getByText('—')).toBeInTheDocument()
    expect(within(rowFor(1)).queryByRole('button')).not.toBeInTheDocument()
    expect(within(rowFor(2)).getByText('Delivered')).toBeInTheDocument()
    expect(within(rowFor(2)).queryByRole('button')).not.toBeInTheDocument()
  })

  it('advances the order and updates the row with the returned status', async () => {
    mockedSellersApi.advanceFulfillment.mockResolvedValue(makeOrder({ fulfillmentStatus: 'SHIPPED' }))
    await renderOrdersTab([makeOrder()])

    fireEvent.click(within(rowFor(42)).getByRole('button', { name: 'Mark as shipped' }))

    expect(await within(rowFor(42)).findByText('Shipped')).toBeInTheDocument()
    expect(mockedSellersApi.advanceFulfillment).toHaveBeenCalledWith(10, 42, 'SHIPPED')
    expect(within(rowFor(42)).getByRole('button', { name: 'Mark as out for delivery' })).toBeInTheDocument()
    expect(screen.getByText('Order #42 marked as shipped.')).toBeInTheDocument()
  })

  it('reloads the list when the order changed in the meantime', async () => {
    mockedSellersApi.advanceFulfillment.mockRejectedValue(new ApiRequestError(409, 'Invalid transition'))
    await renderOrdersTab([makeOrder()])
    expect(mockedSellersApi.getOrders).toHaveBeenCalledTimes(1)

    fireEvent.click(within(rowFor(42)).getByRole('button', { name: 'Mark as shipped' }))

    expect(await screen.findByText('Order #42 changed in the meantime — the list was refreshed.')).toBeInTheDocument()
    await waitFor(() => expect(mockedSellersApi.getOrders).toHaveBeenCalledTimes(2))
  })

  it('disables the button while the update is pending', async () => {
    mockedSellersApi.advanceFulfillment.mockReturnValue(new Promise(() => {}))
    await renderOrdersTab([makeOrder()])

    const button = within(rowFor(42)).getByRole('button', { name: 'Mark as shipped' })
    fireEvent.click(button)

    await waitFor(() => expect(button).toBeDisabled())
  })

  // Behaviour guard only: fireEvent flushes the re-render between clicks, so jsdom can't reproduce
  // the browser race (second click before the disabled re-render) that the ref guard closes.
  it('sends a single request on a double click', async () => {
    mockedSellersApi.advanceFulfillment.mockReturnValue(new Promise(() => {}))
    await renderOrdersTab([makeOrder()])

    const button = within(rowFor(42)).getByRole('button', { name: 'Mark as shipped' })
    fireEvent.click(button)
    fireEvent.click(button)

    await waitFor(() => expect(button).toBeDisabled())
    expect(mockedSellersApi.advanceFulfillment).toHaveBeenCalledTimes(1)
  })

  it("keeps another order's button disabled while its own update is still pending", async () => {
    let finishFirst: (order: SellerOrder) => void = () => {}
    mockedSellersApi.advanceFulfillment
      .mockReturnValueOnce(new Promise((resolve) => (finishFirst = resolve)))
      .mockReturnValueOnce(new Promise(() => {}))
    await renderOrdersTab([makeOrder(), makeOrder({ orderId: 43 })])

    fireEvent.click(within(rowFor(42)).getByRole('button', { name: 'Mark as shipped' }))
    const second = within(rowFor(43)).getByRole('button', { name: 'Mark as shipped' })
    fireEvent.click(second)
    finishFirst(makeOrder({ fulfillmentStatus: 'SHIPPED' }))

    expect(await within(rowFor(42)).findByRole('button', { name: 'Mark as out for delivery' })).toBeEnabled()
    expect(second).toBeDisabled()
  })
})
