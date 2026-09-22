import { render, type RenderResult } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { CartProvider } from '../context/CartContext'

interface RenderOptions {
  route?: string
}

export function renderWithProviders(ui: ReactNode, { route = '/' }: RenderOptions = {}): RenderResult {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <CartProvider>{ui}</CartProvider>
      </AuthProvider>
    </MemoryRouter>,
  )
}

// eslint-disable-next-line react-refresh/only-export-components -- test helper module re-exporting RTL utilities, not a component file
export * from '@testing-library/react'
