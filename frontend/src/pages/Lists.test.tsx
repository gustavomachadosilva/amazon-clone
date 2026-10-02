import { fireEvent, screen } from '@testing-library/react'
import { vi } from 'vitest'
import { Route, Routes, useLocation } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    // CartProvider fetches the cart whenever someone is signed in; keep that off the network.
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
    listsApi: {
      ...actual.listsApi,
      listMine: vi.fn(),
      create: vi.fn(),
    },
    catalogApi: {
      ...actual.catalogApi,
      getById: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { cartApi, catalogApi, listsApi, type WishListView } from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import { ListsProvider } from '../context/ListsContext'
import Lists from './Lists'

const mockedCartApi = vi.mocked(cartApi)
const mockedListsApi = vi.mocked(listsApi)
const mockedCatalogApi = vi.mocked(catalogApi)

const LISTS: WishListView[] = [
  { id: 1, buyerId: 1, name: 'Birthday ideas', productIds: [], createdAt: '2026-09-03T12:00:00Z' },
  { id: 2, buyerId: 1, name: 'Books', productIds: [], createdAt: '2026-09-02T12:00:00Z' },
  { id: 3, buyerId: 1, name: 'Kitchen', productIds: [], createdAt: '2026-09-01T12:00:00Z' },
]

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

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname + location.search}</div>
}

function renderLists(route: string) {
  return renderWithProviders(
    <ListsProvider>
      <Routes>
        <Route path="/lists" element={<Lists />} />
      </Routes>
      <LocationProbe />
    </ListsProvider>,
    { route },
  )
}

function listButton(name: string) {
  return screen.getByRole('button', { name: new RegExp(`^${name}`) })
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  seedAuth()
  mockedCartApi.get.mockResolvedValue({ userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 })
  // Lists arrive after the page mounts, like a direct load of /lists?list=… in the browser.
  mockedListsApi.listMine.mockResolvedValue(LISTS)
  mockedCatalogApi.getById.mockRejectedValue(new Error('not needed'))
})

describe('Lists page', () => {
  it('selects the first list when no list is requested', async () => {
    renderLists('/lists')

    expect(await screen.findByRole('heading', { level: 1, name: 'Birthday ideas' })).toBeInTheDocument()
    expect(listButton('Birthday ideas')).toHaveAttribute('aria-pressed', 'true')
  })

  it('selects the list from ?list= even though the lists load after mount', async () => {
    renderLists('/lists?list=2')

    expect(await screen.findByRole('heading', { level: 1, name: 'Books' })).toBeInTheDocument()
    expect(listButton('Books')).toHaveAttribute('aria-pressed', 'true')
    expect(listButton('Birthday ideas')).toHaveAttribute('aria-pressed', 'false')
  })

  it.each(['999', 'abc', '2abc', '-2'])('falls back to the first list for ?list=%s', async (param) => {
    renderLists(`/lists?list=${param}`)

    expect(await screen.findByRole('heading', { level: 1, name: 'Birthday ideas' })).toBeInTheDocument()
    expect(listButton('Birthday ideas')).toHaveAttribute('aria-pressed', 'true')
  })

  it('selects a list from the sidebar and keeps the URL in sync', async () => {
    renderLists('/lists?list=2')
    await screen.findByRole('heading', { level: 1, name: 'Books' })

    fireEvent.click(listButton('Kitchen'))

    expect(screen.getByRole('heading', { level: 1, name: 'Kitchen' })).toBeInTheDocument()
    expect(listButton('Kitchen')).toHaveAttribute('aria-pressed', 'true')
    expect(listButton('Books')).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByTestId('location')).toHaveTextContent('/lists?list=3')
  })

  it('selects a newly created list and points the URL at it', async () => {
    mockedListsApi.create.mockResolvedValue({
      id: 7,
      buyerId: 1,
      name: 'Garden',
      productIds: [],
      createdAt: '2026-09-04T12:00:00Z',
    })
    renderLists('/lists')
    await screen.findByRole('heading', { level: 1, name: 'Birthday ideas' })

    fireEvent.change(screen.getByPlaceholderText('New list name'), { target: { value: 'Garden' } })
    fireEvent.click(screen.getByRole('button', { name: 'Add' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Garden' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/lists?list=7')
  })
})
