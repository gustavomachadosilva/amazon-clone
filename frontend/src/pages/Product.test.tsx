import { screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    // Cart and lists only fetch for a signed-in user; mocked anyway to keep them off the network.
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
    listsApi: {
      ...actual.listsApi,
      listMine: vi.fn(),
    },
    catalogApi: {
      ...actual.catalogApi,
      getById: vi.fn(),
      related: vi.fn(),
      search: vi.fn(),
    },
    reviewsApi: {
      ...actual.reviewsApi,
      listByProduct: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import {
  catalogApi,
  reviewsApi,
  type Page,
  type Product as ProductType,
  type RelatedProduct,
  type RelatedReason,
} from '../services/api'
import { ListsProvider } from '../context/ListsContext'
import Product from './Product'

const mockedCatalogApi = vi.mocked(catalogApi)
const mockedReviewsApi = vi.mocked(reviewsApi)

function product(id: number, overrides: Partial<ProductType> = {}): ProductType {
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

function related(item: ProductType, primaryReason: RelatedReason): RelatedProduct {
  return { product: item, score: 5, primaryReason, reasons: [primaryReason] }
}

function emptyPage(): Page<ProductType> {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 10,
    first: true,
    last: true,
    empty: true,
  }
}

const CURRENT = product(1, { name: 'Acme Cordless Drill', brand: 'Acme' })

function renderProduct() {
  return renderWithProviders(
    <ListsProvider>
      <Routes>
        <Route path="/product/:id" element={<Product />} />
      </Routes>
    </ListsProvider>,
    { route: '/product/1' },
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mockedCatalogApi.getById.mockResolvedValue(CURRENT)
  mockedCatalogApi.search.mockResolvedValue(emptyPage())
  mockedReviewsApi.listByProduct.mockResolvedValue([])
})

describe('Product page recommendations', () => {
  it('shows each recommendation with the label of the reason it was picked for', async () => {
    mockedCatalogApi.related.mockResolvedValue([
      related(product(2, { brand: 'Acme' }), 'SAME_BRAND'),
      related(product(3, { price: 80 }), 'LOWER_PRICE'),
      related(product(4, { averageRating: 4.9, reviewCount: 12 }), 'TOP_RATED_IN_CATEGORY'),
      related(product(5, { averageRating: 4.1, reviewCount: 3 }), 'HIGHER_RATED'),
      related(product(6), 'SIMILAR_NAME'),
      related(product(7, { category: 'Garden' }), 'SAME_CATEGORY'),
      // Seventh item: goes to the "Recommended based on this item" list, not the grid.
      related(product(8), 'SAME_CATEGORY'),
    ])

    renderProduct()

    expect(await screen.findByRole('heading', { name: 'Similar items' })).toBeInTheDocument()
    expect(screen.getByText('More from Acme')).toBeInTheDocument()
    expect(screen.getByText('Similar item, $20.00 less')).toBeInTheDocument()
    expect(screen.getByText('Highest rated in Tools')).toBeInTheDocument()
    expect(screen.getByText('Rated higher than this item')).toBeInTheDocument()
    expect(screen.getByText('Similar to this item')).toBeInTheDocument()
    expect(screen.getByText('Also in Garden')).toBeInTheDocument()

    expect(screen.getByRole('heading', { name: 'Recommended based on this item' })).toBeInTheDocument()
    expect(screen.getByText('Also in Tools')).toBeInTheDocument()
    expect(mockedCatalogApi.related).toHaveBeenCalledWith(1, 10)
  })

  it('shows no invented shares or browsing-session claims', async () => {
    mockedCatalogApi.related.mockResolvedValue([related(product(2), 'SAME_CATEGORY')])

    renderProduct()

    await screen.findByRole('heading', { name: 'Similar items' })
    expect(screen.queryByText(/also viewed/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/browsing sessions/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/frequently bought with or instead/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /customers who viewed/i })).not.toBeInTheDocument()
  })

  it('renders nothing for a reason the page does not know', async () => {
    mockedCatalogApi.related.mockResolvedValue([related(product(2), 'MOST_VIEWED' as RelatedReason)])

    renderProduct()

    await screen.findByRole('heading', { name: 'Similar items' })
    expect(screen.queryByText(/MOST_VIEWED/)).not.toBeInTheDocument()
  })

  it('hides the similar-items and recommended sections when nothing is related', async () => {
    mockedCatalogApi.related.mockResolvedValue([])

    renderProduct()

    await screen.findByRole('heading', { name: 'Acme Cordless Drill' })
    await waitFor(() => expect(mockedCatalogApi.related).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: 'Similar items' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Recommended based on this item' })).not.toBeInTheDocument()
  })

  it('hides the sections when loading related products fails', async () => {
    mockedCatalogApi.related.mockRejectedValue(new Error('boom'))

    renderProduct()

    await screen.findByRole('heading', { name: 'Acme Cordless Drill' })
    await waitFor(() => expect(mockedCatalogApi.related).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: 'Similar items' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Recommended based on this item' })).not.toBeInTheDocument()
  })
})
