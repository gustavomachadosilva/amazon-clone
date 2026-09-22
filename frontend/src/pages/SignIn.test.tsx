import { fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'
import SignIn from './SignIn'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    usersApi: {
      login: vi.fn(),
      register: vi.fn(),
    },
    // Signing in successfully triggers CartProvider's fetch-on-login effect; stub it too so
    // that flow doesn't make a real network call in the background during this test.
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
  }
})

import { ApiRequestError, cartApi, usersApi } from '../services/api'

const mockedUsersApi = vi.mocked(usersApi)
const mockedCartApi = vi.mocked(cartApi)

function renderSignIn() {
  return renderWithProviders(
    <Routes>
      <Route path="/signin" element={<SignIn />} />
      <Route path="/" element={<div>Home stub</div>} />
    </Routes>,
    { route: '/signin' },
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

describe('SignIn page', () => {
  it('signs in successfully and navigates home', async () => {
    mockedUsersApi.login.mockResolvedValue({
      id: 1,
      name: 'Test Buyer',
      email: 'buyer@example.com',
      role: 'BUYER',
      token: 'test-token',
      expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    })
    mockedCartApi.get.mockResolvedValue({ userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 })

    renderSignIn()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'buyer@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByText('Continue'))

    await waitFor(() => expect(mockedUsersApi.login).toHaveBeenCalledWith('buyer@example.com', 'password123'))
    await waitFor(() => expect(screen.getByText('Home stub')).toBeInTheDocument())
  })

  it('rejects an invalid email client-side without calling the API', async () => {
    renderSignIn()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'not-an-email' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } })
    // Dispatch the submit event directly rather than clicking the submit button: the input
    // has type="email", and a real click would trigger the browser's native email-format
    // constraint validation, which would block the submit before React's handler even runs.
    const form = screen.getByText('Continue').closest('form')
    expect(form).not.toBeNull()
    fireEvent.submit(form as HTMLFormElement)

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent(/valid email/i))
    expect(mockedUsersApi.login).not.toHaveBeenCalled()
  })

  it('shows an error message on server rejection and lets the user retry', async () => {
    mockedUsersApi.login.mockRejectedValue(new ApiRequestError(401))

    renderSignIn()

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'buyer@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrong-password' } })
    fireEvent.click(screen.getByText('Continue'))

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent(/incorrect email or password/i),
    )
    expect(screen.queryByText('Home stub')).not.toBeInTheDocument()

    const submitButton = screen.getByText('Continue')
    expect(submitButton).toBeEnabled()
  })
})
