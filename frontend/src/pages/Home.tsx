import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Blueprint, Placeholder, Button, Card, Input, Select } from '../components/ui'
import ProductGridCard from '../components/ProductGridCard'
import { catalogApi, type Product } from '../services/api'
import { CATEGORIES } from '../lib/constants'

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
    <div className="mx-auto max-w-[1280px] px-4 py-4 md:px-6 md:py-6">
      <Blueprint className="grid grid-cols-1 items-center gap-6 p-5 md:grid-cols-[1.15fr_1fr] md:gap-[34px] md:p-[34px]">
        <div>
          <div className="kick">2026 catalogue · Free shipping over $49</div>
          <h1 className="max-w-[15ch] text-[32px] leading-[1.05] md:text-[52px] md:leading-[1.02]">
            Everything the workshop, the desk and the kitchen need.
          </h1>
          <p className="max-w-[46ch] text-[#5d5d60]">
            Over 40,000 items from 900 sellers, with tracked delivery and 30-day returns.
          </p>
          <form onSubmit={handleSearchSubmit} className="mt-4 flex flex-col gap-2 sm:flex-row">
            <Select value={category} onChange={(e) => setCategory(e.target.value)} className="sm:w-[180px]">
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
            <Button variant="primary" onClick={() => navigate('/search?sort=low')}>
              See today&rsquo;s deals
            </Button>
            <Button variant="secondary" onClick={() => navigate('/search')}>
              Browse catalogue
            </Button>
          </div>
        </div>
        <Placeholder label="Campaign image" aspect="16/10" />
      </Blueprint>

      <div className="mt-10">
        <h2>Shop by category</h2>
        <div className="mt-3 grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-6">
          {CATEGORIES.slice(1).map((name) => (
            <Card
              key={name}
              as="button"
              type="button"
              blueprint
              hoverLift
              className="w-full cursor-pointer p-3.5 text-left"
              onClick={() => navigate(`/search?category=${name}`)}
            >
              <Placeholder label={name} aspect="1/1" />
              <div className="mt-2 text-[15px]">{name}</div>
            </Card>
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
        <div className="mt-3 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {products.slice(0, 10).map((product) => (
            <ProductGridCard key={product.id} product={product} />
          ))}
        </div>
      </div>
    </div>
  )
}
