import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { vi } from 'vitest'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    usersApi: {
      me: vi.fn(),
      login: vi.fn(),
      register: vi.fn(),
      updateMe: vi.fn(),
      changePassword: vi.fn(),
    },
    // The real Header loads categories and CartProvider fetches the cart for signed-in users;
    // keep both off the network.
    catalogApi: {
      ...actual.catalogApi,
      getCategories: vi.fn(),
    },
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { ApiRequestError, cartApi, catalogApi, usersApi, type UserProfile } from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import RequireAuth from '../components/auth/RequireAuth'
import Header from '../components/layout/Header'
import type { UserRole } from '../types/domain'
import Account from './Account'
import SignIn from './SignIn'

const mockedUsersApi = vi.mocked(usersApi)
const mockedCartApi = vi.mocked(cartApi)
const mockedCatalogApi = vi.mocked(catalogApi)

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

function storedUser() {
  return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'null')
}

// Exposes the current URL so tests can check where a redirect landed.
function LocationProbe() {
  const { pathname, hash } = useLocation()
  return <div data-testid="location">{pathname + hash}</div>
}

// The real Header is rendered so tests can check the "Hello, {name}" greeting; the
// /account/security redirect mirrors App.tsx.
function renderAccount(route = '/account') {
  return renderWithProviders(
    <>
      <Header />
      <Routes>
        <Route
          path="/account"
          element={
            <RequireAuth>
              <Account />
            </RequireAuth>
          }
        />
        <Route path="/account/security" element={<Navigate to="/account#account-info" replace />} />
        <Route path="/signin" element={<SignIn />} />
        <Route path="/" element={<div>Home stub</div>} />
      </Routes>
      <LocationProbe />
    </>,
    { route },
  )
}

// Renders the page for a signed-in buyer and waits for the profile (and the Header's
// categories) to settle.
async function renderLoadedAccount(route = '/account') {
  seedAuth('BUYER')
  mockedUsersApi.me.mockResolvedValue(profileFor('BUYER'))
  renderAccount(route)
  await profileCard().findByText('Test Buyer')
  await act(async () => {})
}

function profileCard() {
  return within(screen.getByRole('region', { name: 'Profile' }))
}

function accountInfo() {
  return within(screen.getByRole('region', { name: 'Your account information' }))
}

function fillPassword(current: string, next: string) {
  fireEvent.change(accountInfo().getByLabelText('Current password'), { target: { value: current } })
  fireEvent.change(accountInfo().getByLabelText('New password'), { target: { value: next } })
  fireEvent.change(accountInfo().getByLabelText('Confirm new password'), { target: { value: next } })
  fireEvent.click(accountInfo().getByRole('button', { name: 'Save password' }))
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mockedCatalogApi.getCategories.mockResolvedValue([])
  mockedCartApi.get.mockResolvedValue({ userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 })
  // jsdom has no layout, so it doesn't implement scrollIntoView.
  Element.prototype.scrollIntoView = vi.fn()
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
    expect(within(profile).queryByRole('status')).not.toBeInTheDocument()
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
    await renderLoadedAccount()

    const shortcuts = screen.getByRole('list', { name: 'Account shortcuts' })
    expect(within(shortcuts).getAllByRole('listitem')).toHaveLength(2)
    expect(within(shortcuts).getByRole('link', { name: /Your Orders/ })).toHaveAttribute('href', '/orders')
    expect(within(shortcuts).getByRole('link', { name: /Your Lists/ })).toHaveAttribute('href', '/lists')
    // Name, email and password are edited on the page itself now.
    expect(within(shortcuts).queryByText('Login & security')).not.toBeInTheDocument()
    expect(within(shortcuts).queryByText('Seller Central')).not.toBeInTheDocument()
    expect(within(shortcuts).getByRole('heading', { level: 3, name: 'Your Orders' })).toBeInTheDocument()
    expect(within(shortcuts).getByRole('heading', { level: 3, name: 'Your Lists' })).toBeInTheDocument()
  })

  it('shows the Seller badge and Seller Central shortcut to sellers', async () => {
    seedAuth('SELLER')
    mockedUsersApi.me.mockResolvedValue(profileFor('SELLER'))

    renderAccount()

    expect(await profileCard().findByText('Seller')).toBeInTheDocument()
    const shortcuts = screen.getByRole('list', { name: 'Account shortcuts' })
    expect(within(shortcuts).getAllByRole('listitem')).toHaveLength(3)
    expect(within(shortcuts).queryByText('Login & security')).not.toBeInTheDocument()
    expect(within(shortcuts).getByRole('link', { name: /Seller Central/ })).toHaveAttribute('href', '/seller')
    expect(within(shortcuts).getByRole('heading', { level: 3, name: 'Seller Central' })).toBeInTheDocument()
  })

  it('shows the account information and lists sections', async () => {
    await renderLoadedAccount()

    expect(screen.getByRole('heading', { level: 2, name: 'Your account information' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: 'Your Lists' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Your Lists' })).toBeInTheDocument()
    expect(accountInfo().getByText('Test Buyer')).toBeInTheDocument()
    expect(accountInfo().getByText('buyer@example.com')).toBeInTheDocument()
    expect(accountInfo().getByRole('button', { name: 'Edit name and email' })).toBeInTheDocument()
    expect(accountInfo().getByRole('button', { name: 'Change password' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open your lists' })).toHaveAttribute('href', '/lists')
  })

  it('signs out, goes home and clears the stored session', async () => {
    await renderLoadedAccount()

    fireEvent.click(profileCard().getByRole('button', { name: 'Sign out' }))

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

    const form = screen.getByRole('button', { name: 'Continue' }).closest('form')!
    fireEvent.change(within(form).getByLabelText('Email'), { target: { value: 'buyer@example.com' } })
    fireEvent.change(within(form).getByLabelText('Password'), { target: { value: 'password123' } })
    fireEvent.click(within(form).getByText('Continue'))

    expect(await screen.findByRole('heading', { name: 'Your Account' })).toBeInTheDocument()
    expect(await profileCard().findByText('Test Buyer')).toBeInTheDocument()
  })

  describe('editing account information', () => {
    it('opens the name and email form in place and Cancel discards the changes', async () => {
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      expect(accountInfo().getByLabelText('Name')).toHaveFocus()
      expect(accountInfo().getByLabelText('Name')).toHaveValue('Test Buyer')
      expect(accountInfo().getByLabelText('Email')).toHaveValue('buyer@example.com')

      fireEvent.change(accountInfo().getByLabelText('Name'), { target: { value: 'Draft Name' } })
      fireEvent.click(accountInfo().getByRole('button', { name: 'Cancel' }))

      expect(accountInfo().queryByLabelText('Name')).not.toBeInTheDocument()
      expect(accountInfo().getByText('Test Buyer')).toBeInTheDocument()
      expect(accountInfo().getByRole('button', { name: 'Edit name and email' })).toHaveFocus()
      expect(mockedUsersApi.updateMe).not.toHaveBeenCalled()

      // Reopening starts again from the saved values.
      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      expect(accountInfo().getByLabelText('Name')).toHaveValue('Test Buyer')
    })

    it('saves the name and email and updates the profile card and header right away', async () => {
      mockedUsersApi.updateMe.mockResolvedValue({ ...profileFor('BUYER'), name: 'New Name', email: 'new@example.com' })
      await renderLoadedAccount()
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      fireEvent.change(accountInfo().getByLabelText('Name'), { target: { value: 'New Name' } })
      fireEvent.change(accountInfo().getByLabelText('Email'), { target: { value: 'new@example.com' } })
      fireEvent.click(accountInfo().getByRole('button', { name: 'Save' }))

      expect(await accountInfo().findByRole('status')).toHaveTextContent('Your name and email have been updated.')
      expect(mockedUsersApi.updateMe).toHaveBeenCalledWith({ name: 'New Name', email: 'new@example.com' })
      expect(accountInfo().queryByLabelText('Name')).not.toBeInTheDocument()
      expect(accountInfo().getByText('New Name')).toBeInTheDocument()
      expect(accountInfo().getByText('new@example.com')).toBeInTheDocument()
      expect(profileCard().getByText('New Name')).toBeInTheDocument()
      expect(profileCard().getByText('new@example.com')).toBeInTheDocument()
      expect(screen.getByText('Hello, New Name')).toBeInTheDocument()
      expect(accountInfo().getByRole('button', { name: 'Edit name and email' })).toHaveFocus()
      await waitFor(() =>
        expect(storedUser()).toMatchObject({ name: 'New Name', email: 'new@example.com', token: 'test-token' }),
      )
    })

    it('keeps the form open with the error when the email belongs to another account', async () => {
      mockedUsersApi.updateMe.mockRejectedValue(new ApiRequestError(409, 'Email already registered'))
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      fireEvent.change(accountInfo().getByLabelText('Email'), { target: { value: 'taken@example.com' } })
      fireEvent.click(accountInfo().getByRole('button', { name: 'Save' }))

      expect(await accountInfo().findByText('That email is already in use by another account.')).toBeInTheDocument()
      expect(accountInfo().getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
      expect(profileCard().getByText('buyer@example.com')).toBeInTheDocument()
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()
      expect(storedUser()).toMatchObject({ name: 'Test Buyer', email: 'buyer@example.com' })
    })

    it('changes the password in place and keeps the user signed in', async () => {
      mockedUsersApi.changePassword.mockResolvedValue(undefined)
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Change password' }))
      expect(accountInfo().getByLabelText('Current password')).toHaveFocus()

      fillPassword('oldpassword', 'newpassword123')

      expect(await accountInfo().findByRole('status')).toHaveTextContent('Your password has been changed.')
      expect(mockedUsersApi.changePassword).toHaveBeenCalledWith({
        currentPassword: 'oldpassword',
        newPassword: 'newpassword123',
      })
      expect(accountInfo().queryByLabelText('Current password')).not.toBeInTheDocument()
      expect(accountInfo().getByRole('button', { name: 'Change password' })).toHaveFocus()
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()
      expect(storedUser()).toMatchObject({ token: 'test-token' })
    })

    it('keeps the password form open when the current password is wrong', async () => {
      mockedUsersApi.changePassword.mockRejectedValue(new ApiRequestError(400, 'Senha atual incorreta'))
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Change password' }))
      fillPassword('wrongpassword', 'newpassword123')

      expect(await accountInfo().findByText('Your current password is incorrect.')).toBeInTheDocument()
      expect(accountInfo().getByLabelText('Current password')).toHaveAttribute('aria-invalid', 'true')
      expect(accountInfo().queryByText('Your password has been changed.')).not.toBeInTheDocument()
    })

    it('opens only one form at a time', async () => {
      await renderLoadedAccount()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Edit name and email' }))
      expect(accountInfo().getByLabelText('Name')).toBeInTheDocument()

      fireEvent.click(accountInfo().getByRole('button', { name: 'Change password' }))
      expect(accountInfo().getByLabelText('Current password')).toHaveFocus()
      expect(accountInfo().queryByLabelText('Name')).not.toBeInTheDocument()
      expect(accountInfo().getByRole('button', { name: 'Edit name and email' })).toBeInTheDocument()
    })

    it('redirects the old Login & security page to the account information section', async () => {
      await renderLoadedAccount('/account/security')

      expect(screen.getByRole('heading', { level: 1, name: 'Your Account' })).toBeInTheDocument()
      expect(screen.getByTestId('location')).toHaveTextContent('/account#account-info')
      const section = screen.getByRole('region', { name: 'Your account information' })
      expect(section).toHaveAttribute('id', 'account-info')
      expect(vi.mocked(Element.prototype.scrollIntoView).mock.contexts).toContain(section)
    })

    it('scrolls to the account information section when linked directly', async () => {
      await renderLoadedAccount('/account#account-info')

      const section = screen.getByRole('region', { name: 'Your account information' })
      expect(vi.mocked(Element.prototype.scrollIntoView).mock.contexts).toContain(section)
    })
  })
})
