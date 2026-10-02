import { screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { renderWithProviders } from '../test/test-utils'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    catalogApi: {
      ...actual.catalogApi,
      search: vi.fn(),
      getCategories: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { catalogApi, type Page, type Product } from '../services/api'
import Search from './Search'

const mockedCatalogApi = vi.mocked(catalogApi)

function product(id: number, name: string, price: number): Product {
  return {
    id,
    name,
    description: '',
    price,
    stockQuantity: 10,
    category: 'Tools',
    sellerId: 1,
    brand: null,
    warrantyMonths: null,
    modelNumber: null,
    listPrice: null,
    averageRating: 4.5,
    reviewCount: 2,
  }
}

function page(content: Product[], totalElements: number, number = 0): Page<Product> {
  return {
    content,
    totalElements,
    totalPages: Math.ceil(totalElements / 10),
    number,
    size: 10,
    first: number === 0,
    last: false,
    empty: content.length === 0,
  }
}

function renderSearch(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/search" element={<Search />} />
    </Routes>,
    { route },
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  mockedCatalogApi.getCategories.mockResolvedValue(['Tools'])
  mockedCatalogApi.search.mockResolvedValue(page([], 0))
})

describe('Search page', () => {
  it('sends every filter, the sort and the 0-based page to the API', async () => {
    renderSearch('/search?q=drill&category=Tools&maxPrice=200&minRating=4&sort=price_asc&page=2')

    await waitFor(() => expect(mockedCatalogApi.search).toHaveBeenCalled())
    expect(mockedCatalogApi.search).toHaveBeenLastCalledWith({
      query: 'drill',
      category: 'Tools',
      maxPrice: 200,
      minRating: 4,
      sort: 'price_asc',
      page: 1,
    })
  })

  it('leaves out the "no limit" price, the rating when "all" and the category when "All"', async () => {
    renderSearch('/search?maxPrice=600')

    await waitFor(() => expect(mockedCatalogApi.search).toHaveBeenCalled())
    expect(mockedCatalogApi.search).toHaveBeenLastCalledWith({
      query: undefined,
      category: undefined,
      maxPrice: undefined,
      minRating: undefined,
      sort: 'relevance',
      page: 0,
    })
  })

  it.each([
    ['low', 'price_asc'],
    ['high', 'price_desc'],
  ])('maps the legacy sort=%s link to %s', async (legacy, expected) => {
    renderSearch(`/search?sort=${legacy}`)

    await waitFor(() => expect(mockedCatalogApi.search).toHaveBeenCalled())
    expect(mockedCatalogApi.search).toHaveBeenLastCalledWith(expect.objectContaining({ sort: expected }))
    expect(screen.getByRole('combobox')).toHaveValue(expected)
  })

  it('renders results in the order the API returns them and the total count', async () => {
    mockedCatalogApi.search.mockResolvedValue(
      page([product(3, 'Expensive saw', 90), product(1, 'Cheap hammer', 5), product(2, 'Mid drill', 40)], 23),
    )

    renderSearch('/search?q=tool')

    expect(await screen.findByText(/23 results/)).toBeInTheDocument()
    const cards = screen.getAllByRole('link', { name: /^View / }).map((card) => card.getAttribute('aria-label'))
    expect(cards).toEqual(['View Expensive saw', 'View Cheap hammer', 'View Mid drill'])
    // Pagination reports the server-side total, not the size of the current page.
    expect(screen.getByRole('navigation', { name: 'Pagination' })).toHaveTextContent('of 23 results')
  })

  it('no longer offers the fake "Arrives tomorrow" filter', async () => {
    renderSearch('/search')

    await waitFor(() => expect(mockedCatalogApi.search).toHaveBeenCalled())
    expect(screen.queryByText('Arrives tomorrow')).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })
})
