import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from './AuthContext'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import type { AuthUser } from '../types/domain'

function Consumer() {
  const { user } = useAuth()
  return <div data-testid="user">{user ? user.email : 'none'}</div>
}

function UpdateButton() {
  const { updateUser } = useAuth()
  return (
    <button type="button" onClick={() => updateUser({ name: 'New Name', email: 'new@example.com' })}>
      Update
    </button>
  )
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

  it('updateUser merges name and email into the session, keeping the token, and persists it', async () => {
    const user: AuthUser = {
      id: 1,
      name: 'Test Buyer',
      email: 'buyer@example.com',
      role: 'BUYER',
      token: 'valid-token',
      tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user))

    render(
      <AuthProvider>
        <Consumer />
        <UpdateButton />
      </AuthProvider>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Update' }))

    expect(screen.getByTestId('user')).toHaveTextContent('new@example.com')
    await waitFor(() => {
      const stored = JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'null')
      expect(stored).toEqual({ ...user, name: 'New Name', email: 'new@example.com' })
    })
  })

  it('updateUser does nothing when signed out', () => {
    render(
      <AuthProvider>
        <Consumer />
        <UpdateButton />
      </AuthProvider>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Update' }))

    expect(screen.getByTestId('user')).toHaveTextContent('none')
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'null')).toBeNull()
  })
})
