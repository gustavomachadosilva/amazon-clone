import { useState } from 'react'
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { Search, Menu, X, User, ShoppingCart } from 'lucide-react'
import { Button } from '../ui'
import { useAuth } from '../../context/AuthContext'
import { useCart } from '../../context/CartContext'
import { CATEGORIES, STORE_NAME } from '../../lib/constants'
import { onEnterKey } from '../../lib/a11y'

export default function Header() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const { user } = useAuth()
  const { itemCount } = useCart()
  const [menuOpen, setMenuOpen] = useState(false)

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
    setMenuOpen(false)
    runSearch()
  }

  function goDepartment(name: string) {
    setQuery('')
    setCategory(name)
    const params = new URLSearchParams()
    if (name !== 'All') params.set('category', name)
    navigate(`/search?${params.toString()}`)
    setMenuOpen(false)
  }

  function goTo(path: string) {
    navigate(path)
    setMenuOpen(false)
  }

  return (
    <header className="relative bg-accent-900 text-[#f2f2f3]">
      <div className="mx-auto flex max-w-[1280px] items-center gap-3 px-4 py-3 md:gap-5 md:px-6">
        <button
          type="button"
          className="-ml-2 flex h-11 w-11 flex-none items-center justify-center md:hidden"
          aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          {menuOpen ? <X size={22} strokeWidth={1.5} /> : <Menu size={22} strokeWidth={1.5} />}
        </button>

        <div
          className="flex-none cursor-pointer"
          role="link"
          tabIndex={0}
          onClick={() => goTo('/')}
          onKeyDown={onEnterKey(() => goTo('/'))}
        >
          <div className="h text-xl uppercase leading-none tracking-[.06em] md:text-[26px]">{STORE_NAME}</div>
          <div className="hidden text-[10px] uppercase tracking-[.2em] text-accent-400 md:block">Marketplace</div>
        </div>

        <form onSubmit={onSubmit} className="hidden max-w-[720px] flex-1 border border-accent-700 bg-[#f2f2f3] md:flex">
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="border-0 border-r border-divider bg-neutral-200 px-2 text-[12.5px] text-foreground"
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
            className="flex-1 border-0 px-2.5 text-sm text-foreground"
          />
          <Button type="submit" variant="primary" className="rounded-none border-0 px-[18px] text-sm tracking-[.08em] uppercase">
            <Search size={16} strokeWidth={1.5} />
          </Button>
        </form>

        <div className="ml-auto flex flex-none items-center gap-1 md:hidden">
          <button
            type="button"
            className="flex h-11 w-11 items-center justify-center"
            aria-label={user ? 'Account' : 'Sign in'}
            onClick={() => goTo(user ? '/orders' : '/signin')}
          >
            <User size={22} strokeWidth={1.5} />
          </button>
          <button
            type="button"
            className="relative flex h-11 w-11 items-center justify-center"
            aria-label={`Cart, ${itemCount} item${itemCount === 1 ? '' : 's'}`}
            onClick={() => goTo('/cart')}
          >
            <ShoppingCart size={22} strokeWidth={1.5} />
            {itemCount > 0 && (
              <span className="h absolute right-0.5 top-0.5 flex h-4 min-w-4 items-center justify-center bg-accent-400 px-1 text-[10px] leading-none text-accent-900">
                {itemCount}
              </span>
            )}
          </button>
        </div>

        <div className="hidden flex-none items-center gap-3 text-xs md:flex">
          <div
            className="cursor-pointer"
            role="link"
            tabIndex={0}
            onClick={() => goTo(user ? '/orders' : '/signin')}
            onKeyDown={onEnterKey(() => goTo(user ? '/orders' : '/signin'))}
          >
            <div className="text-accent-400">{user ? `Hello, ${user.name}` : 'Hello, sign in'}</div>
            <div className="h text-[13px]">Account &amp; Lists</div>
          </div>
          <div
            className="cursor-pointer"
            role="link"
            tabIndex={0}
            onClick={() => goTo('/orders')}
            onKeyDown={onEnterKey(() => goTo('/orders'))}
          >
            <div className="text-accent-400">Returns</div>
            <div className="h text-[13px]">&amp; Orders</div>
          </div>
          <div
            className="flex cursor-pointer items-center gap-1.5 border border-accent-700 px-2.5 py-1.5"
            role="link"
            tabIndex={0}
            aria-label={`Cart, ${itemCount} item${itemCount === 1 ? '' : 's'}`}
            onClick={() => goTo('/cart')}
            onKeyDown={onEnterKey(() => goTo('/cart'))}
          >
            <span className="text-[11px] uppercase tracking-[.08em]">Cart</span>
            <span className="h text-xl text-accent-400">{itemCount}</span>
          </div>
        </div>
      </div>

      <div className="px-4 pb-3 md:hidden">
        <form onSubmit={onSubmit} className="flex border border-accent-700 bg-[#f2f2f3]">
          <select
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="max-w-[38%] border-0 border-r border-divider bg-neutral-200 px-2 text-[12.5px] text-foreground"
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
            placeholder="Search products"
            className="min-h-11 flex-1 border-0 px-2.5 text-sm text-foreground"
          />
          <Button type="submit" variant="primary" className="min-h-11 rounded-none border-0 px-4">
            <Search size={18} strokeWidth={1.5} />
          </Button>
        </form>
      </div>

      <div className="hidden border-t border-accent-700 bg-accent-800 md:block">
        <div className="mx-auto flex max-w-[1280px] flex-wrap items-center px-6">
          <button className="navlink" onClick={() => goDepartment('All')}>
            All departments
          </button>
          {CATEGORIES.slice(1).map((name) => (
            <button key={name} className="navlink" onClick={() => goDepartment(name)}>
              {name}
            </button>
          ))}
          <button className="navlink ml-auto text-accent-400" onClick={() => navigate('/lists')}>
            Your Lists
          </button>
          <button className="navlink text-accent-400" onClick={() => navigate('/seller')}>
            Seller Central
          </button>
        </div>
      </div>

      {menuOpen && (
        <div className="absolute inset-x-0 top-full z-30 flex flex-col border-t border-accent-700 bg-accent-800 shadow-ds-lg md:hidden">
          <button className="navlink min-h-11 text-left" onClick={() => goDepartment('All')}>
            All departments
          </button>
          {CATEGORIES.slice(1).map((name) => (
            <button key={name} className="navlink min-h-11 text-left" onClick={() => goDepartment(name)}>
              {name}
            </button>
          ))}
          <div className="mx-3 my-1 h-px bg-accent-700" />
          <button className="navlink min-h-11 text-left" onClick={() => goTo(user ? '/orders' : '/signin')}>
            {user ? `Hello, ${user.name}` : 'Hello, sign in'}
          </button>
          <button className="navlink min-h-11 text-left" onClick={() => goTo('/orders')}>
            Returns &amp; Orders
          </button>
          <button className="navlink min-h-11 text-left text-accent-400" onClick={() => goTo('/lists')}>
            Your Lists
          </button>
          <button className="navlink min-h-11 text-left text-accent-400" onClick={() => goTo('/seller')}>
            Seller Central
          </button>
        </div>
      )}
    </header>
  )
}
