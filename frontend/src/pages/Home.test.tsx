import { fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    // The cart only fetches for a signed-in user; mocked anyway to keep it off the network.
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
    catalogApi: {
      ...actual.catalogApi,
      getCategories: vi.fn(),
    },
    recommendationsApi: {
      home: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import {
  ApiRequestError,
  cartApi,
  catalogApi,
  recommendationsApi,
  type HomeRecommendations,
  type Product,
} from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import { useAuth } from '../context/AuthContext'
import Home from './Home'

const mockedRecommendationsApi = vi.mocked(recommendationsApi)

function product(id: number, overrides: Partial<Product> = {}): Product {
  return {
    id,
    name: `Product ${id}`,
    description: 'A product',
    price: 100,
    stockQuantity: 10,
    category: 'Tools',
    sellerId: 1,
    brand: null,
    warrantyMonths: null,
    modelNumber: null,
    listPrice: null,
    averageRating: 0,
    reviewCount: 0,
    ...overrides,
  }
}

function shelf(layer: HomeRecommendations['layer'], ...items: Product[]): HomeRecommendations {
  return {
    layer,
    items: items.map((item) => ({
      product: item,
      reason: layer === 'PERSONALIZED' ? 'CATEGORY_AFFINITY' : 'TOP_RATED',
    })),
  }
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

function SignOutButton() {
  const { signOut } = useAuth()
  return (
    <button type="button" onClick={signOut}>
      Test sign out
    </button>
  )
}

function renderHome() {
  return renderWithProviders(
    <>
      <SignOutButton />
      <Home />
    </>,
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  vi.mocked(catalogApi.getCategories).mockResolvedValue([])
  vi.mocked(cartApi.get).mockResolvedValue({ userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 })
})

describe('Home product shelf', () => {
  it('shows anonymous visitors the "Top rated" shelf', async () => {
    mockedRecommendationsApi.home.mockResolvedValue(shelf('TOP_RATED', product(1, { name: 'Best Drill' })))

    renderHome()

    expect(await screen.findByRole('heading', { name: 'Top rated' })).toBeInTheDocument()
    expect(screen.getAllByText('Best Drill').length).toBeGreaterThan(0)
    expect(screen.queryByRole('heading', { name: 'Recommended for you' })).not.toBeInTheDocument()
    expect(mockedRecommendationsApi.home).toHaveBeenCalledWith(12)
  })

  it('says "Recommended for you" only for a personalized shelf', async () => {
    seedAuth()
    mockedRecommendationsApi.home.mockResolvedValue(shelf('PERSONALIZED', product(2, { name: 'Picked Saw' })))

    renderHome()

    expect(await screen.findByRole('heading', { name: 'Recommended for you' })).toBeInTheDocument()
    expect(screen.getAllByText('Picked Saw').length).toBeGreaterThan(0)
    expect(screen.queryByRole('heading', { name: 'Top rated' })).not.toBeInTheDocument()
  })

  it('reloads the shelf when the signed-in user changes', async () => {
    seedAuth()
    mockedRecommendationsApi.home
      .mockResolvedValueOnce(shelf('PERSONALIZED', product(2, { name: 'Picked Saw' })))
      .mockResolvedValueOnce(shelf('TOP_RATED', product(1, { name: 'Best Drill' })))

    renderHome()
    expect(await screen.findByRole('heading', { name: 'Recommended for you' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Test sign out' }))

    expect(await screen.findByRole('heading', { name: 'Top rated' })).toBeInTheDocument()
    expect(screen.queryAllByText('Picked Saw')).toHaveLength(0)
    expect(mockedRecommendationsApi.home).toHaveBeenCalledTimes(2)
  })

  it('shows a skeleton without a heading while loading', async () => {
    let resolve: (value: HomeRecommendations) => void = () => {}
    mockedRecommendationsApi.home.mockReturnValue(new Promise((r) => (resolve = r)))

    const { container } = renderHome()

    expect(screen.getAllByText('Loading recommendations…').length).toBeGreaterThan(0)
    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull()
    expect(screen.queryByRole('heading', { name: 'Top rated' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Recommended for you' })).not.toBeInTheDocument()

    resolve(shelf('TOP_RATED', product(1)))
    expect(await screen.findByRole('heading', { name: 'Top rated' })).toBeInTheDocument()
    expect(container.querySelector('[aria-busy="true"]')).toBeNull()
  })

  it('hides the section when there is nothing to recommend', async () => {
    mockedRecommendationsApi.home.mockResolvedValue(shelf('TOP_RATED'))

    renderHome()

    await waitFor(() => expect(screen.queryByText('Loading recommendations…')).not.toBeInTheDocument())
    expect(screen.queryByRole('heading', { name: 'Top rated' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'See all' })).not.toBeInTheDocument()
  })

  it('hides the section when the request fails', async () => {
    mockedRecommendationsApi.home.mockRejectedValue(new ApiRequestError(500))

    renderHome()

    await waitFor(() => expect(screen.queryByText('Loading recommendations…')).not.toBeInTheDocument())
    expect(screen.queryByRole('heading', { name: 'Top rated' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Recommended for you' })).not.toBeInTheDocument()
  })
})
