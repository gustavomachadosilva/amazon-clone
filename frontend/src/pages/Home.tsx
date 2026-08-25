import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import Placeholder from '../components/ui/Placeholder'
import ProductGridCard from '../components/ProductGridCard'
import { catalogApi, type Product } from '../services/api'
import { CATEGORIES } from '../lib/constants'

export default function Home() {
  const navigate = useNavigate()
  const [products, setProducts] = useState<Product[]>([])

  useEffect(() => {
    catalogApi.search().then((page) => setProducts(page.content))
  }, [])

  return (
    <div style={{ maxWidth: 1280, margin: '0 auto', padding: '24px' }}>
      <Blueprint
        style={{
          padding: 34,
          display: 'grid',
          gridTemplateColumns: '1.15fr 1fr',
          gap: 34,
          alignItems: 'center',
        }}
      >
        <div>
          <div className="kick">2026 catalogue · Free shipping over $49</div>
          <h1 style={{ fontSize: 52, lineHeight: 1.02, maxWidth: '15ch' }}>
            Everything the workshop, the desk and the kitchen need.
          </h1>
          <p style={{ maxWidth: '46ch', color: '#5d5d60' }}>
            Over 40,000 items from 900 sellers, with tracked delivery and 30-day returns.
          </p>
          <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
            <button className="btn btn-primary" onClick={() => navigate('/search?sort=low')}>
              See today&rsquo;s deals
            </button>
            <button className="btn btn-secondary" onClick={() => navigate('/search')}>
              Browse catalogue
            </button>
          </div>
        </div>
        <Placeholder label="Campaign image" aspect="16/10" />
      </Blueprint>

      <div style={{ marginTop: 40 }}>
        <h2>Shop by category</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: 16, marginTop: 12 }}>
          {CATEGORIES.slice(1).map((name) => (
            <Blueprint
              key={name}
              className="prod"
              style={{ padding: 14, cursor: 'pointer' }}
              onClick={() => navigate(`/search?category=${name}`)}
            >
              <Placeholder label={name} aspect="1/1" />
              <div style={{ fontSize: 15, marginTop: 8 }}>{name}</div>
            </Blueprint>
          ))}
        </div>
      </div>

      <div style={{ marginTop: 40 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <h2>Recommended for you</h2>
          <button className="btn btn-ghost" onClick={() => navigate('/search')}>
            See all
          </button>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 16, marginTop: 12 }}>
          {products.slice(0, 10).map((product) => (
            <ProductGridCard key={product.id} product={product} />
          ))}
        </div>
      </div>
    </div>
  )
}
