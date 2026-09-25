import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>()
  return {
    ...actual,
    catalogApi: {
      ...actual.catalogApi,
      getCategories: vi.fn(),
    },
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
    listsApi: {
      ...actual.listsApi,
      listMine: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { cartApi, catalogApi, listsApi } from '../../services/api'
import { AUTH_STORAGE_KEY } from '../../services/auth-token'
import { AuthProvider } from '../../context/AuthContext'
import { CartProvider } from '../../context/CartContext'
import { ListsProvider, useLists } from '../../context/ListsContext'
import RequireRole from '../auth/RequireRole'
import Header from './Header'

const mockedCatalogApi = vi.mocked(catalogApi)
const mockedCartApi = vi.mocked(cartApi)
const mockedListsApi = vi.mocked(listsApi)

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

function ListsProbe() {
  const { lists } = useLists()
  return <div data-testid="lists-count">{lists.length}</div>
}

function renderHeader(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <CartProvider>
          <ListsProvider>
            <Header />
            <ListsProbe />
            <Routes>
              <Route path="/" element={<div>Home stub</div>} />
              <Route path="/orders" element={<div>Orders stub</div>} />
              <Route path="/signin" element={<div>SignIn stub</div>} />
              <Route
                path="/protected"
                element={
                  <RequireRole role="BUYER">
                    <div>Protected stub</div>
                  </RequireRole>
                }
              />
            </Routes>
          </ListsProvider>
        </CartProvider>
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function waitForSignedInState() {
  expect(await screen.findByText('Hello, Test Buyer')).toBeInTheDocument()
  await waitFor(() => expect(screen.getByTestId('lists-count')).toHaveTextContent('1'))
  await waitFor(() => expect(screen.getAllByLabelText('Cart, 2 items')).toHaveLength(2))
}

async function expectSignedOutState() {
  expect(await screen.findByText('Home stub')).toBeInTheDocument()
  expect(screen.getByText('Hello, sign in')).toBeInTheDocument()
  await waitFor(() => expect(screen.getAllByLabelText('Cart, 0 items')).toHaveLength(2))
  expect(screen.getByTestId('lists-count')).toHaveTextContent('0')
  expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'undefined')).toBeNull()
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mockedCatalogApi.getCategories.mockResolvedValue([])
  mockedCartApi.get.mockResolvedValue({
    userId: AUTH_USER.id,
    items: [
      {
        productId: 1,
        productName: 'Widget',
        unitPrice: 10,
        quantity: 2,
        lineTotal: 20,
        savedForLater: false,
      },
    ],
    savedForLater: [],
    itemCount: 2,
    total: 20,
  })
  mockedListsApi.listMine.mockResolvedValue([
    { id: 1, buyerId: AUTH_USER.id, name: 'Wishlist', productIds: [1], createdAt: '2026-01-01T00:00:00Z' },
  ])
})

describe('Header sign out', () => {
  it('signs out from the desktop account block, goes home and clears cart and lists', async () => {
    seedAuth()
    renderHeader('/orders')
    await waitForSignedInState()

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    await expectSignedOutState()
    expect(screen.queryByText('Orders stub')).not.toBeInTheDocument()
  })

  it('signs out from the mobile menu and closes it', async () => {
    seedAuth()
    renderHeader('/orders')
    await waitForSignedInState()

    fireEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    const mobileMenu = screen.getByRole('navigation', { name: 'Mobile menu' })
    fireEvent.click(within(mobileMenu).getByRole('button', { name: 'Sign out' }))

    await expectSignedOutState()
    expect(screen.queryByRole('navigation', { name: 'Mobile menu' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Open menu' })).toBeInTheDocument()
  })

  it('lands on home, not the sign-in redirect, when signing out from a protected route', async () => {
    seedAuth()
    renderHeader('/protected')
    await waitForSignedInState()
    expect(screen.getByText('Protected stub')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByText('Home stub')).toBeInTheDocument()
    expect(screen.queryByText('SignIn stub')).not.toBeInTheDocument()
    expect(screen.queryByText('Protected stub')).not.toBeInTheDocument()
  })

  it('does not offer Sign out when nobody is signed in', async () => {
    renderHeader('/')
    expect(await screen.findByText('Home stub')).toBeInTheDocument()
    expect(screen.getByText('Hello, sign in')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    const mobileMenu = screen.getByRole('navigation', { name: 'Mobile menu' })
    expect(within(mobileMenu).queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })
})
