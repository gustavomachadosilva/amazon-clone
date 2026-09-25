import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { AUTH_STORAGE_KEY } from '../../services/auth-token'
import { AuthProvider } from '../../context/AuthContext'
import RequireAuth from './RequireAuth'
import RequireRole from './RequireRole'

function SignInProbe() {
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string } } | null)?.from
  return <div>SignIn stub from {from?.pathname ?? 'nowhere'}</div>
}

function seedAuth() {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      id: 1,
      name: 'Test Buyer',
      email: 'buyer@example.com',
      role: 'BUYER',
      token: 'test-token',
      tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }),
  )
}

function renderAt(route: string) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <Routes>
          <Route
            path="/account"
            element={
              <RequireAuth>
                <div>Account stub</div>
              </RequireAuth>
            }
          />
          <Route
            path="/seller"
            element={
              <RequireRole role="SELLER">
                <div>Seller stub</div>
              </RequireRole>
            }
          />
          <Route path="/signin" element={<SignInProbe />} />
          <Route path="/" element={<div>Home stub</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  localStorage.clear()
})

describe('RequireAuth / RequireRole', () => {
  it('redirects signed-out visitors to sign in, remembering where they came from', () => {
    renderAt('/account')
    expect(screen.getByText('SignIn stub from /account')).toBeInTheDocument()
  })

  it('renders the page for any signed-in user', () => {
    seedAuth()
    renderAt('/account')
    expect(screen.getByText('Account stub')).toBeInTheDocument()
  })

  it('still asks signed-out visitors to sign in on role-protected routes', () => {
    renderAt('/seller')
    expect(screen.getByText('SignIn stub from /seller')).toBeInTheDocument()
  })

  it('sends a signed-in user with the wrong role home', () => {
    seedAuth()
    renderAt('/seller')
    expect(screen.getByText('Home stub')).toBeInTheDocument()
  })
})
