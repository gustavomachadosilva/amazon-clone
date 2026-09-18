import { render } from '@testing-library/react'
import { expect, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'

// Mock the API to prevent real fetch calls during tests
vi.mock('./services/api', () => ({
  catalogApi: {
    search: vi.fn(() => Promise.resolve({ content: [] })),
    getCategories: vi.fn(() => Promise.resolve([])),
  },
  ordersApi: {
    listMyOrders: vi.fn(() => Promise.resolve([])),
  },
  usersApi: {
    getMe: vi.fn(() => Promise.resolve({ id: 1, name: 'Test', role: 'BUYER' })),
  },
  listsApi: {
    listMine: vi.fn(() => Promise.resolve([])),
  }
}))
import { AuthProvider } from './context/AuthContext'
import { CartProvider } from './context/CartContext'
import { ListsProvider } from './context/ListsContext'
import App from './App'

describe('App Component', () => {
  it('renders the App successfully', () => {
    // Just a smoke test to ensure setup is correct
    render(
      <MemoryRouter>
        <AuthProvider>
          <CartProvider>
            <ListsProvider>
              <App />
            </ListsProvider>
          </CartProvider>
        </AuthProvider>
      </MemoryRouter>
    )
    expect(document.body).toBeInTheDocument()
  })
})
