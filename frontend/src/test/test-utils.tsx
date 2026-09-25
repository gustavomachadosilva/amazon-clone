import { render, type RenderResult } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter, type MemoryRouterProps } from 'react-router-dom'
import { AuthProvider } from '../context/AuthContext'
import { CartProvider } from '../context/CartContext'

interface RenderOptions {
  // A path, or a { pathname, state } entry when the test needs location state.
  route?: NonNullable<MemoryRouterProps['initialEntries']>[number]
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
