import { render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from './AuthContext'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import type { AuthUser } from '../types/domain'

function Consumer() {
  const { user } = useAuth()
  return <div data-testid="user">{user ? user.email : 'none'}</div>
}

function renderConsumer() {
  return render(
    <AuthProvider>
      <Consumer />
    </AuthProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
})

describe('AuthContext', () => {
  it('exposes the stored user when the token has not expired', () => {
    const user: AuthUser = {
      id: 1,
      name: 'Test Buyer',
      email: 'buyer@example.com',
      role: 'BUYER',
      token: 'valid-token',
      tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user))

    renderConsumer()

    expect(screen.getByTestId('user')).toHaveTextContent('buyer@example.com')
  })

  it('treats an expired session as signed out and clears the stale entry from localStorage', async () => {
    const user: AuthUser = {
      id: 1,
      name: 'Test Buyer',
      email: 'buyer@example.com',
      role: 'BUYER',
      token: 'expired-token',
      tokenExpiresAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
    }
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user))

    renderConsumer()

    expect(screen.getByTestId('user')).toHaveTextContent('none')

    await waitFor(() => {
      const stored = JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'null')
      expect(stored).toBeNull()
    })
  })
})
