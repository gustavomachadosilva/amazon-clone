import { fireEvent, screen, waitFor, within } from '@testing-library/react'
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
    },
    ordersApi: {
      ...actual.ordersApi,
      boughtTogether: vi.fn(),
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
  ordersApi,
  reviewsApi,
  type BoughtTogether,
  type Product as ProductType,
  type RelatedProduct,
  type RelatedReason,
} from '../services/api'
import { ListsProvider } from '../context/ListsContext'
import Product from './Product'

const mockedCatalogApi = vi.mocked(catalogApi)
const mockedReviewsApi = vi.mocked(reviewsApi)
const mockedOrdersApi = vi.mocked(ordersApi)

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

function coPurchase(...items: [ProductType, number][]): BoughtTogether {
  return {
    source: 'CO_PURCHASE',
    items: items.map(([item, times]) => ({ product: item, timesBoughtTogether: times, primaryReason: null })),
  }
}

function similarBundle(...items: [ProductType, RelatedReason][]): BoughtTogether {
  return {
    source: 'SIMILAR',
    items: items.map(([item, reason]) => ({ product: item, timesBoughtTogether: null, primaryReason: reason })),
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
  mockedOrdersApi.boughtTogether.mockResolvedValue(coPurchase())
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

  it("never shows the previous product's recommendations while the next one's are loading", async () => {
    const next = product(2, { name: 'Next Product' })
    mockedCatalogApi.getById.mockImplementation(async (id: number) => (id === 1 ? CURRENT : next))
    mockedCatalogApi.related.mockImplementation((id: number) =>
      id === 1 ? Promise.resolve([related(next, 'SAME_CATEGORY')]) : new Promise<RelatedProduct[]>(() => {}),
    )

    renderProduct()

    await screen.findByRole('heading', { name: 'Similar items' })
    fireEvent.click(screen.getAllByRole('link', { name: /Next Product/ })[0])

    expect(await screen.findByRole('heading', { name: 'Next Product' })).toBeInTheDocument()
    await waitFor(() => expect(mockedCatalogApi.related).toHaveBeenCalledWith(2, 10))
    expect(screen.queryByRole('heading', { name: 'Similar items' })).not.toBeInTheDocument()
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

describe('Product page bundle', () => {
  beforeEach(() => {
    mockedCatalogApi.related.mockResolvedValue([])
  })

  it('shows items bought together with how many customers bought them', async () => {
    mockedOrdersApi.boughtTogether.mockResolvedValue(
      coPurchase([product(20, { name: 'Drill Bits' }), 3], [product(21, { name: 'Safety Glasses' }), 2]),
    )

    renderProduct()

    expect(await screen.findByRole('heading', { name: 'Frequently bought together' })).toBeInTheDocument()
    expect(screen.getAllByText('Drill Bits').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Safety Glasses').length).toBeGreaterThan(0)
    expect(screen.getByText('Bought together by 3 customers')).toBeInTheDocument()
    expect(screen.getByText('Bought together by 2 customers')).toBeInTheDocument()
    expect(screen.getByText('3 of 3 items selected')).toBeInTheDocument()
    expect(screen.queryByText(/not enough purchase history/i)).not.toBeInTheDocument()
    expect(mockedOrdersApi.boughtTogether).toHaveBeenCalledWith(1, 2)
  })

  it('labels the similarity fallback as such and never as bought together', async () => {
    mockedOrdersApi.boughtTogether.mockResolvedValue(
      similarBundle([product(30, { name: 'Acme Impact Driver', brand: 'Acme' }), 'SAME_BRAND']),
    )

    renderProduct()

    expect(await screen.findByRole('heading', { name: 'Pairs well with this item' })).toBeInTheDocument()
    expect(screen.getByText('Not enough purchase history yet — suggested by similarity to this item')).toBeInTheDocument()
    expect(screen.getByText('More from Acme')).toBeInTheDocument()
    expect(screen.queryByText(/bought together/i)).not.toBeInTheDocument()
  })

  it('does not repeat a similarity bundle item in the similar-items grid', async () => {
    mockedCatalogApi.related.mockResolvedValue([
      related(product(30, { name: 'Bundled Similar' }), 'SAME_CATEGORY'),
      related(product(31, { name: 'Only In Grid' }), 'SAME_CATEGORY'),
    ])
    mockedOrdersApi.boughtTogether.mockResolvedValue(similarBundle([product(30, { name: 'Bundled Similar' }), 'SAME_CATEGORY']))

    renderProduct()

    await screen.findByRole('heading', { name: 'Pairs well with this item' })
    const grid = (await screen.findByRole('heading', { name: 'Similar items' })).parentElement as HTMLElement
    expect(within(grid).getAllByText('Only In Grid').length).toBeGreaterThan(0)
    expect(within(grid).queryAllByText('Bundled Similar')).toHaveLength(0)
    expect(mockedCatalogApi.related).toHaveBeenCalledWith(1, 10)
  })

  it('hides the block when there is nothing to offer', async () => {
    mockedOrdersApi.boughtTogether.mockResolvedValue(similarBundle())

    renderProduct()

    await screen.findByRole('heading', { name: 'Acme Cordless Drill' })
    await waitFor(() => expect(mockedOrdersApi.boughtTogether).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: 'Frequently bought together' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Pairs well with this item' })).not.toBeInTheDocument()
    expect(screen.queryByText('Add selected to cart')).not.toBeInTheDocument()
  })

  it('hides the block when loading it fails', async () => {
    mockedOrdersApi.boughtTogether.mockRejectedValue(new Error('boom'))

    renderProduct()

    await screen.findByRole('heading', { name: 'Acme Cordless Drill' })
    await waitFor(() => expect(mockedOrdersApi.boughtTogether).toHaveBeenCalled())
    expect(screen.queryByText('Add selected to cart')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Recommended based on this item' })).not.toBeInTheDocument()
  })
})
