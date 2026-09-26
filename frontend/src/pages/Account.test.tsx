import { fireEvent, screen, within } from '@testing-library/react'
import { vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    usersApi: {
      me: vi.fn(),
      login: vi.fn(),
      register: vi.fn(),
    },
    // CartProvider fetches the cart whenever someone is signed in; keep that off the network.
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { cartApi, usersApi, type UserProfile } from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import RequireAuth from '../components/auth/RequireAuth'
import type { UserRole } from '../types/domain'
import Account from './Account'
import SignIn from './SignIn'

const mockedUsersApi = vi.mocked(usersApi)
const mockedCartApi = vi.mocked(cartApi)

function profileFor(role: UserRole): UserProfile {
  return {
    id: 1,
    name: role === 'SELLER' ? 'Test Seller' : 'Test Buyer',
    email: role === 'SELLER' ? 'seller@example.com' : 'buyer@example.com',
    role,
    createdAt: '2026-03-15T12:00:00Z',
  }
}

function seedAuth(role: UserRole) {
  const { id, name, email } = profileFor(role)
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      id,
      name,
      email,
      role,
      token: 'test-token',
      tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }),
  )
}

function renderAccount() {
  return renderWithProviders(
    <Routes>
      <Route
        path="/account"
        element={
          <RequireAuth>
            <Account />
          </RequireAuth>
        }
      />
      <Route path="/signin" element={<SignIn />} />
      <Route path="/" element={<div>Home stub</div>} />
    </Routes>,
    { route: '/account' },
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mockedCartApi.get.mockResolvedValue({ userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 })
})

describe('Account page', () => {
  it('shows a loading state, then the buyer profile', async () => {
    seedAuth('BUYER')
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))

    renderAccount()

    expect(screen.getByRole('heading', { name: 'Your Account' })).toBeInTheDocument()
    const profile = screen.getByRole('region', { name: 'Profile' })
    expect(within(profile).getByRole('status')).toHaveTextContent('Loading your account')
    // Sign out lives in the profile card in every state, including while it loads.
    expect(within(profile).getByRole('button', { name: 'Sign out' })).toBeInTheDocument()

    expect(await within(profile).findByText('Test Buyer')).toBeInTheDocument()
    expect(within(profile).getByText('buyer@example.com')).toBeInTheDocument()
    expect(within(profile).getByText('Buyer')).toBeInTheDocument()
    expect(within(profile).getByText('Member since March 2026')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows an error when the profile fails to load and retries on demand', async () => {
    seedAuth('BUYER')
    mockedUsersApi.me.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(profileFor('BUYER'))

    renderAccount()
    const profile = screen.getByRole('region', { name: 'Profile' })

    expect(await within(profile).findByRole('alert')).toHaveTextContent("We couldn't load your account details.")
    expect(within(profile).getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
    fireEvent.click(within(profile).getByRole('button', { name: 'Try again' }))

    expect(await within(profile).findByText('Test Buyer')).toBeInTheDocument()
    expect(mockedUsersApi.me).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('labels buyers with a neutral solid role label', async () => {
    seedAuth('BUYER')
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))

    renderAccount()
    const profile = screen.getByRole('region', { name: 'Profile' })
    await within(profile).findByText('Test Buyer')

    expect(within(profile).getByText('Buyer')).toHaveClass('bg-paper-200')
    expect(within(profile).queryByText('Seller')).not.toBeInTheDocument()
  })

  it('labels sellers with an accent role label', async () => {
    seedAuth('SELLER')
    mockedUsersApi.me.mockResolvedValue(profileFor('SELLER'))

    renderAccount()
    const profile = screen.getByRole('region', { name: 'Profile' })
    await within(profile).findByText('Test Seller')

    expect(within(profile).getByText('Seller')).toHaveClass('bg-accent-600')
    expect(within(profile).queryByText('Buyer')).not.toBeInTheDocument()
  })

  it('offers buyer shortcuts without Seller Central', async () => {
    seedAuth('BUYER')
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))

    renderAccount()
    await screen.findByText('Test Buyer')

    const shortcuts = screen.getByRole('list', { name: 'Account shortcuts' })
    expect(within(shortcuts).getAllByRole('listitem')).toHaveLength(3)
    expect(within(shortcuts).getByRole('link', { name: /Your Orders/ })).toHaveAttribute('href', '/orders')
    expect(within(shortcuts).getByRole('link', { name: /Your Lists/ })).toHaveAttribute('href', '/lists')
    expect(within(shortcuts).getByRole('link', { name: /Login & security/ })).toHaveAttribute(
      'href',
      '/account/security',
    )
    expect(screen.queryByText('Seller Central')).not.toBeInTheDocument()
    expect(within(shortcuts).getByRole('heading', { level: 3, name: 'Your Orders' })).toBeInTheDocument()
    expect(within(shortcuts).getByRole('heading', { level: 3, name: 'Your Lists' })).toBeInTheDocument()
    expect(within(shortcuts).getByRole('heading', { level: 3, name: 'Login & security' })).toBeInTheDocument()
  })

  it('shows the Seller badge and Seller Central shortcut to sellers', async () => {
    seedAuth('SELLER')
    mockedUsersApi.me.mockResolvedValue(profileFor('SELLER'))

    renderAccount()

    expect(await screen.findByText('Seller')).toBeInTheDocument()
    const shortcuts = screen.getByRole('list', { name: 'Account shortcuts' })
    expect(within(shortcuts).getAllByRole('listitem')).toHaveLength(4)
    expect(within(shortcuts).getByRole('link', { name: /Seller Central/ })).toHaveAttribute('href', '/seller')
    expect(within(shortcuts).getByRole('heading', { level: 3, name: 'Seller Central' })).toBeInTheDocument()
  })

  it('shows the account information and lists sections', async () => {
    seedAuth('BUYER')
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))

    renderAccount()
    await screen.findByText('Test Buyer')

    expect(screen.getByRole('heading', { level: 2, name: 'Your account information' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Your Lists' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Your account information' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Your Lists' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Edit account details' })).toHaveAttribute('href', '/account/security')
    expect(screen.getByRole('link', { name: 'Open your lists' })).toHaveAttribute('href', '/lists')
  })

  it('signs out, goes home and clears the stored session', async () => {
    seedAuth('BUYER')
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))

    renderAccount()
    const profile = screen.getByRole('region', { name: 'Profile' })
    await within(profile).findByText('Test Buyer')

    fireEvent.click(within(profile).getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByText('Home stub')).toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'undefined')).toBeNull()
  })

  it('sends signed-out visitors to sign in and brings them back afterwards', async () => {
    mockedUsersApi.login.mockResolvedValue({
      ...profileFor('BUYER'),
      token: 'test-token',
      expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    })
    mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))

    renderAccount()

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(mockedUsersApi.me).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'buyer@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByText('Continue'))

    expect(await screen.findByRole('heading', { name: 'Your Account' })).toBeInTheDocument()
    expect(await screen.findByText('Test Buyer')).toBeInTheDocument()
  })
})
