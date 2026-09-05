import { useState } from 'react'
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { Search } from 'lucide-react'
import { Button } from '../ui'
import { useAuth } from '../../context/AuthContext'
import { useCart } from '../../context/CartContext'
import { CATEGORIES, STORE_NAME } from '../../lib/constants'

export default function Header() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const { user } = useAuth()
  const { itemCount } = useCart()

  const onSearchScreen = location.pathname === '/search'
  const [query, setQuery] = useState(onSearchScreen ? (searchParams.get('q') ?? '') : '')
  const [category, setCategory] = useState(onSearchScreen ? (searchParams.get('category') ?? 'All') : 'All')

  function runSearch(overrideCategory?: string) {
    const params = new URLSearchParams()
    if (query) params.set('q', query)
    const cat = overrideCategory ?? category
    if (cat && cat !== 'All') params.set('category', cat)
    navigate(`/search?${params.toString()}`)
  }

  function onSubmit(event: React.FormEvent) {
    event.preventDefault()
    runSearch()
  }

  function goDepartment(name: string) {
    setQuery('')
    setCategory(name)
    const params = new URLSearchParams()
    if (name !== 'All') params.set('category', name)
    navigate(`/search?${params.toString()}`)
  }

  return (
    <header style={{ background: 'var(--color-accent-900)', color: '#f2f2f3' }}>
      <div
        style={{
          maxWidth: 1280,
          margin: '0 auto',
          padding: '12px 24px',
          display: 'flex',
          alignItems: 'center',
          gap: 20,
        }}
      >
        <div style={{ cursor: 'pointer', flex: 'none' }} onClick={() => navigate('/')}>
          <div className="h" style={{ fontSize: 26, letterSpacing: '.06em', textTransform: 'uppercase' }}>
            {STORE_NAME}
          </div>
          <div style={{ fontSize: 10, letterSpacing: '.2em', textTransform: 'uppercase', color: 'var(--color-accent-400)' }}>
            Marketplace
          </div>
        </div>

        <form
          onSubmit={onSubmit}
          style={{
            flex: 1,
            maxWidth: 720,
            display: 'flex',
            border: '1px solid var(--color-accent-700)',
            background: '#f2f2f3',
          }}
        >
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            style={{
              background: '#e7e7ea',
              border: 0,
              borderRight: '1px solid var(--color-divider)',
              color: '#1d1f20',
              fontSize: 12.5,
              padding: '0 8px',
            }}
          >
            {CATEGORIES.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search products, brands and categories"
            style={{ flex: 1, border: 0, padding: '0 10px', color: '#1d1f20', fontSize: 14 }}
          />
          <Button
            type="submit"
            variant="primary"
            style={{
              border: 0,
              borderRadius: 0,
              fontSize: 14,
              letterSpacing: '.08em',
              textTransform: 'uppercase',
              padding: '0 18px',
            }}
          >
            <Search size={16} strokeWidth={1.5} />
          </Button>
        </form>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 12, flex: 'none' }}>
          <div style={{ cursor: 'pointer' }} onClick={() => navigate(user ? '/orders' : '/signin')}>
            <div style={{ color: 'var(--color-accent-400)' }}>{user ? `Hello, ${user.name}` : 'Hello, sign in'}</div>
            <div className="h" style={{ fontSize: 13 }}>
              Account &amp; Lists
            </div>
          </div>
          <div style={{ cursor: 'pointer' }} onClick={() => navigate('/orders')}>
            <div style={{ color: 'var(--color-accent-400)' }}>Returns</div>
            <div className="h" style={{ fontSize: 13 }}>
              &amp; Orders
            </div>
          </div>
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              border: '1px solid var(--color-accent-700)',
              padding: '6px 10px',
              cursor: 'pointer',
            }}
            onClick={() => navigate('/cart')}
          >
            <span style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '.08em' }}>Cart</span>
            <span className="h" style={{ fontSize: 20, color: 'var(--color-accent-400)' }}>
              {itemCount}
            </span>
          </div>
        </div>
      </div>

      <div style={{ background: 'var(--color-accent-800)', borderTop: '1px solid var(--color-accent-700)' }}>
        <div style={{ maxWidth: 1280, margin: '0 auto', padding: '0 24px', display: 'flex', alignItems: 'center', flexWrap: 'wrap' }}>
          <button className="navlink" onClick={() => goDepartment('All')}>
            All departments
          </button>
          {CATEGORIES.slice(1).map((name) => (
            <button key={name} className="navlink" onClick={() => goDepartment(name)}>
              {name}
            </button>
          ))}
          <button
            className="navlink"
            style={{ marginLeft: 'auto', color: 'var(--color-accent-400)' }}
            onClick={() => navigate('/lists')}
          >
            Your Lists
          </button>
          <button className="navlink" style={{ color: 'var(--color-accent-400)' }} onClick={() => navigate('/seller')}>
            Seller Central
          </button>
        </div>
      </div>
    </header>
  )
}
