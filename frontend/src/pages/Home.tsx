import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Blueprint, Placeholder, Button, Input, Select } from '../components/ui'
import ProductGridCard from '../components/ProductGridCard'
import { catalogApi, type Product } from '../services/api'

export default function Home() {
  const navigate = useNavigate()
  const [products, setProducts] = useState<Product[]>([])
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('All')
  const [categories, setCategories] = useState<string[]>([])

  useEffect(() => {
    catalogApi.search().then((page) => setProducts(page.content))
  }, [])

  useEffect(() => {
    catalogApi.getCategories().then(setCategories)
  }, [])

  function handleSearchSubmit(event: React.FormEvent) {
    event.preventDefault()
    const params = new URLSearchParams()
    if (query) params.set('q', query)
    if (category !== 'All') params.set('category', category)
    navigate(`/search?${params.toString()}`)
  }

  return (
    <div className="w-full px-4 py-4 md:px-8 md:py-6 lg:px-10">
      <Blueprint className="grid grid-cols-1 items-center gap-8 bg-card p-6 md:grid-cols-[1.15fr_1fr] md:gap-10 md:p-12">
        <div>
          <h1 className="max-w-[14ch] text-[50px] leading-[1.02] md:text-[86px]">
            Everything the workshop, the desk and the kitchen need.
          </h1>
          <p className="mt-3 max-w-[46ch] text-[19px] text-paper-700">
            Over 40,000 items from 900 sellers, with tracked delivery and 30-day returns.
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1.5 border-y border-dashed border-divider py-2.5 font-mono text-[15px] uppercase tracking-wide text-paper-600">
            <span>Manifest no. 2026-000841</span>
            <span aria-hidden="true">·</span>
            <span>900 sellers on file</span>
            <span aria-hidden="true">·</span>
            <span>Free freight over $49</span>
          </div>
          <form onSubmit={handleSearchSubmit} className="mt-5 flex flex-col gap-2 sm:flex-row">
            <Select value={category} onChange={(e) => setCategory(e.target.value)} className="sm:w-[190px]">
              <option value="All">All categories</option>
              {categories.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </Select>
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search products"
              className="sm:flex-1"
            />
            <Button type="submit" variant="primary">
              Search
            </Button>
          </form>
          <div className="mt-4 flex flex-col gap-3 sm:flex-row">
            <Button variant="primary" onClick={() => navigate('/search?sort=price_asc')}>
              See today&rsquo;s deals
            </Button>
            <Button variant="secondary" onClick={() => navigate('/search')}>
              Browse catalogue
            </Button>
          </div>
        </div>
        <div className="relative">
          <Placeholder label="Sample shipment" aspect="16/10" />
          <span
            className="stamp absolute -bottom-4 -left-4 origin-bottom-left text-[17px]"
            style={{ transform: 'rotate(-8deg) scale(1.3)' }}
          >
            Verified cargo
          </span>
        </div>
      </Blueprint>

      <div className="mt-12">
        <h2 className="text-[26px]">Shop by category</h2>
        <div className="mt-3.5 flex flex-wrap gap-3">
          {categories.map((name) => (
            <button
              key={name}
              type="button"
              className="tag tag-accent-2 cursor-pointer py-2 pl-6 pr-4 text-[15.5px] hover:bg-tag-200"
              onClick={() => navigate(`/search?category=${encodeURIComponent(name)}`)}
            >
              {name}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-10">
        <div className="flex items-center justify-between">
          <h2>Recommended for you</h2>
          <Button variant="ghost" onClick={() => navigate('/search')}>
            See all
          </Button>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6">
          {products.slice(0, 12).map((product) => (
            <ProductGridCard key={product.id} product={product} />
          ))}
        </div>
      </div>
    </div>
  )
}
