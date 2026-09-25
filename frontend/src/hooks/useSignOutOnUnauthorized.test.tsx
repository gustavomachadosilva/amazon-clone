import { act, fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { AuthProvider, useAuth } from '../context/AuthContext'
import { notifyUnauthorized } from '../services/auth-events'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import { useSignOutOnUnauthorized } from './useSignOutOnUnauthorized'

function storeSession(token: string) {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      id: 1,
      name: 'Test Buyer',
      email: 'buyer@example.com',
      role: 'BUYER',
      token,
      tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }),
  )
}

function Harness({ children }: { children?: ReactNode }) {
  useSignOutOnUnauthorized()
  const { user } = useAuth()
  const navigate = useNavigate()
  return (
    <>
      <div data-testid="user">{user?.email ?? 'signed out'}</div>
      <button onClick={() => navigate('/orders')}>Go to orders</button>
      {children}
    </>
  )
}

function SignInStub() {
  const from = (useLocation().state as { from?: { pathname: string } } | null)?.from
  return <div>Sign in stub (from: {from?.pathname ?? 'none'})</div>
}

function renderAt(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <Harness>
          <Routes>
            <Route path="/orders" element={<div>Orders stub</div>} />
            <Route path="/signin" element={<SignInStub />} />
          </Routes>
        </Harness>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  localStorage.clear()
})

describe('useSignOutOnUnauthorized', () => {
  it('signs out and redirects to /signin remembering the current page', () => {
    storeSession('token-1')
    renderAt('/orders')
    expect(screen.getByTestId('user')).toHaveTextContent('buyer@example.com')

    act(() => {
      // Parallel requests failing with the same token must only be handled once.
      notifyUnauthorized('token-1')
      notifyUnauthorized('token-1')
    })

    expect(screen.getByText('Sign in stub (from: /orders)')).toBeInTheDocument()
    expect(screen.getByTestId('user')).toHaveTextContent('signed out')
    expect(JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'null')).toBeNull()
  })

  it('signs out without navigating when already on /signin', () => {
    storeSession('token-1')
    renderAt('/signin')

    act(() => {
      notifyUnauthorized('token-1')
    })

    expect(screen.getByText('Sign in stub (from: none)')).toBeInTheDocument()
    expect(screen.getByTestId('user')).toHaveTextContent('signed out')
  })

  it('handles a later rejection of a different token', () => {
    storeSession('token-1')
    renderAt('/orders')

    act(() => {
      notifyUnauthorized('token-1')
    })
    expect(screen.getByText('Sign in stub (from: /orders)')).toBeInTheDocument()

    // Back on a page, a rejection for a new session (e.g. after signing in again) must still redirect.
    fireEvent.click(screen.getByText('Go to orders'))
    expect(screen.getByText('Orders stub')).toBeInTheDocument()

    act(() => {
      notifyUnauthorized('token-2')
    })
    expect(screen.getByText('Sign in stub (from: /orders)')).toBeInTheDocument()
  })
})
