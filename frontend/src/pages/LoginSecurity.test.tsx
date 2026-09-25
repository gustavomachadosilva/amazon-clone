import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
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
import { ApiRequestError, cartApi, catalogApi, usersApi } from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import RequireAuth from '../components/auth/RequireAuth'
import Header from '../components/layout/Header'
import LoginSecurity from './LoginSecurity'
import SignIn from './SignIn'

const mockedUsersApi = vi.mocked(usersApi)
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

function storedUser() {
  return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'null')
}

// The Header loads categories and CartProvider loads the cart on mount; let those settle so their
// state updates land inside act() instead of after the test has moved on.
async function renderPage() {
  const result = renderWithProviders(
    <>
      <Header />
      <Routes>
        <Route
          path="/account/security"
          element={
            <RequireAuth>
              <LoginSecurity />
            </RequireAuth>
          }
        />
        <Route path="/signin" element={<SignIn />} />
      </Routes>
    </>,
    { route: '/account/security' },
  )
  await act(async () => {})
  return result
}

function profileForm() {
  return within(screen.getByRole('region', { name: 'Name and email' }))
}

function passwordForm() {
  return within(screen.getByRole('region', { name: 'Password' }))
}

function fillPassword(current: string, next: string, confirm = next) {
  const form = passwordForm()
  fireEvent.change(form.getByLabelText('Current password'), { target: { value: current } })
  fireEvent.change(form.getByLabelText('New password'), { target: { value: next } })
  fireEvent.change(form.getByLabelText('Confirm new password'), { target: { value: confirm } })
  fireEvent.click(form.getByRole('button', { name: 'Change password' }))
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mockedCatalogApi.getCategories.mockResolvedValue([])
  mockedCartApi.get.mockResolvedValue({ userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 })
})

describe('Login & security page', () => {
  it('shows the breadcrumb and prefills the profile form from the session', async () => {
    seedAuth()
    await renderPage()

    expect(screen.getByRole('heading', { level: 1, name: 'Login & security' })).toBeInTheDocument()
    expect(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('link', { name: 'Your Account' })).toHaveAttribute(
      'href',
      '/account',
    )
    expect(profileForm().getByLabelText('Name')).toHaveValue('Test Buyer')
    expect(profileForm().getByLabelText('Email')).toHaveValue('buyer@example.com')
  })

  it('sends signed-out visitors to sign in', async () => {
    await renderPage()

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Login & security' })).not.toBeInTheDocument()
  })

  describe('name and email', () => {
    it('saves the changes and updates the header greeting right away', async () => {
      seedAuth()
      mockedUsersApi.updateMe.mockResolvedValue({
        id: 1,
        name: 'New Name',
        email: 'new@example.com',
        role: 'BUYER',
        createdAt: '2026-03-15T12:00:00Z',
      })
      await renderPage()
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()

      const form = profileForm()
      fireEvent.change(form.getByLabelText('Name'), { target: { value: '  New Name  ' } })
      fireEvent.change(form.getByLabelText('Email'), { target: { value: 'new@example.com' } })
      fireEvent.click(form.getByRole('button', { name: 'Save changes' }))

      expect(await form.findByRole('status')).toHaveTextContent('Your name and email have been updated.')
      expect(mockedUsersApi.updateMe).toHaveBeenCalledWith({ name: 'New Name', email: 'new@example.com' })
      expect(screen.getByText('Hello, New Name')).toBeInTheDocument()
      expect(form.getByLabelText('Name')).toHaveValue('New Name')
      await waitFor(() =>
        expect(storedUser()).toMatchObject({ name: 'New Name', email: 'new@example.com', token: 'test-token' }),
      )
    })

    it('shows a clear message when the email belongs to another account', async () => {
      seedAuth()
      mockedUsersApi.updateMe.mockRejectedValue(new ApiRequestError(409, 'Email already registered'))
      await renderPage()

      const form = profileForm()
      fireEvent.change(form.getByLabelText('Name'), { target: { value: 'New Name' } })
      fireEvent.change(form.getByLabelText('Email'), { target: { value: 'taken@example.com' } })
      fireEvent.click(form.getByRole('button', { name: 'Save changes' }))

      expect(await form.findByText('That email is already in use by another account.')).toBeInTheDocument()
      expect(form.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()
      expect(storedUser()).toMatchObject({ name: 'Test Buyer', email: 'buyer@example.com' })
    })

    it('validates the fields before calling the API', async () => {
      seedAuth()
      await renderPage()

      const form = profileForm()
      fireEvent.change(form.getByLabelText('Name'), { target: { value: '   ' } })
      fireEvent.change(form.getByLabelText('Email'), { target: { value: 'not-an-email' } })
      fireEvent.click(form.getByRole('button', { name: 'Save changes' }))

      expect(form.getByText('Enter your name.')).toBeInTheDocument()
      expect(form.getByText('Enter a valid email address.')).toBeInTheDocument()
      expect(mockedUsersApi.updateMe).not.toHaveBeenCalled()
    })

    it('rejects values longer than 255 characters', async () => {
      seedAuth()
      await renderPage()

      const form = profileForm()
      fireEvent.change(form.getByLabelText('Name'), { target: { value: 'a'.repeat(256) } })
      fireEvent.click(form.getByRole('button', { name: 'Save changes' }))

      expect(form.getByText('Must be 255 characters or fewer.')).toBeInTheDocument()
      expect(mockedUsersApi.updateMe).not.toHaveBeenCalled()
    })

    it('does not call the API when nothing changed', async () => {
      seedAuth()
      await renderPage()

      const form = profileForm()
      fireEvent.click(form.getByRole('button', { name: 'Save changes' }))

      expect(form.getByRole('status')).toHaveTextContent('No changes to save.')
      expect(mockedUsersApi.updateMe).not.toHaveBeenCalled()
    })

    it('shows a generic error when saving fails unexpectedly', async () => {
      seedAuth()
      mockedUsersApi.updateMe.mockRejectedValue(new Error('network down'))
      await renderPage()

      const form = profileForm()
      fireEvent.change(form.getByLabelText('Name'), { target: { value: 'New Name' } })
      fireEvent.click(form.getByRole('button', { name: 'Save changes' }))

      expect(await form.findByRole('alert')).toHaveTextContent('Could not save your changes. Please try again.')
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()
    })
  })

  describe('password', () => {
    it('changes the password, clears the fields and keeps the user signed in', async () => {
      seedAuth()
      mockedUsersApi.changePassword.mockResolvedValue(undefined)
      await renderPage()

      fillPassword('oldpassword', 'newpassword123')

      const form = passwordForm()
      expect(await form.findByRole('status')).toHaveTextContent('Your password has been changed.')
      expect(mockedUsersApi.changePassword).toHaveBeenCalledWith({
        currentPassword: 'oldpassword',
        newPassword: 'newpassword123',
      })
      expect(form.getByLabelText('Current password')).toHaveValue('')
      expect(form.getByLabelText('New password')).toHaveValue('')
      expect(form.getByLabelText('Confirm new password')).toHaveValue('')
      expect(screen.getByText('Hello, Test Buyer')).toBeInTheDocument()
      expect(storedUser()).toMatchObject({ token: 'test-token' })
    })

    it('says so when the current password is wrong', async () => {
      seedAuth()
      mockedUsersApi.changePassword.mockRejectedValue(new ApiRequestError(400, 'Senha atual incorreta'))
      await renderPage()

      fillPassword('wrongpassword', 'newpassword123')

      const form = passwordForm()
      expect(await form.findByText('Your current password is incorrect.')).toBeInTheDocument()
      expect(form.getByLabelText('Current password')).toHaveAttribute('aria-invalid', 'true')
      expect(form.queryByRole('status')).not.toBeInTheDocument()
    })

    it('requires the current password and at least 8 characters for the new one', async () => {
      seedAuth()
      await renderPage()

      fillPassword('', 'short')

      const form = passwordForm()
      expect(form.getByText('Enter your current password.')).toBeInTheDocument()
      expect(form.getByText('Password must be at least 8 characters.')).toBeInTheDocument()
      expect(mockedUsersApi.changePassword).not.toHaveBeenCalled()
    })

    it('rejects a confirmation that does not match', async () => {
      seedAuth()
      await renderPage()

      fillPassword('oldpassword', 'newpassword123', 'newpassword124')

      expect(passwordForm().getByText('Passwords do not match.')).toBeInTheDocument()
      expect(mockedUsersApi.changePassword).not.toHaveBeenCalled()
    })

    it('rejects a new password equal to the current one', async () => {
      seedAuth()
      await renderPage()

      fillPassword('samepassword', 'samepassword')

      expect(passwordForm().getByText('Your new password must be different from the current one.')).toBeInTheDocument()
      expect(mockedUsersApi.changePassword).not.toHaveBeenCalled()
    })

    it('measures the 72-character limit in bytes, like bcrypt', async () => {
      seedAuth()
      await renderPage()

      // 40 characters but 80 bytes in UTF-8: must be caught here, not reported as a wrong
      // current password after the backend rejects it.
      fillPassword('oldpassword', 'é'.repeat(40))

      expect(passwordForm().getByText(/Password is too long/)).toBeInTheDocument()
      expect(mockedUsersApi.changePassword).not.toHaveBeenCalled()
    })

    it('accepts a multi-byte password that fits in 72 bytes', async () => {
      seedAuth()
      mockedUsersApi.changePassword.mockResolvedValue(undefined)
      await renderPage()

      fillPassword('oldpassword', 'é'.repeat(36))

      expect(await passwordForm().findByRole('status')).toHaveTextContent('Your password has been changed.')
    })

    it('tells the user to sign in again when the session has expired', async () => {
      seedAuth()
      mockedUsersApi.changePassword.mockRejectedValue(new ApiRequestError(401))
      await renderPage()

      fillPassword('oldpassword', 'newpassword123')

      expect(await passwordForm().findByRole('alert')).toHaveTextContent('Your session has expired. Please sign in again.')
    })
  })
})
