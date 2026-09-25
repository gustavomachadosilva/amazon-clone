import { useState } from 'react'
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { Search, Menu, X, User, ShoppingCart } from 'lucide-react'
import { Button } from '../ui'
import { useAuth } from '../../context/AuthContext'
import { useCart } from '../../context/CartContext'
import { HEADER_HIGHLIGHT_CATEGORIES, STORE_NAME } from '../../lib/constants'
import { useCategories } from '../../hooks/useCategories'
import { onEnterKey } from '../../lib/a11y'
import { useSignOut } from '../../hooks/useSignOut'

export default function Header() {
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const { user } = useAuth()
  const { itemCount } = useCart()
  const signOut = useSignOut()
  const [menuOpen, setMenuOpen] = useState(false)
  const categories = useCategories()
  const departmentLinks = categories.filter((name) => HEADER_HIGHLIGHT_CATEGORIES.includes(name))

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

  function handleSignOut() {
    setMenuOpen(false)
    signOut()
  }

  return (
    <header className="relative bg-accent-900 text-paper-50">
      <div className="flex w-full items-center gap-4 px-4 py-3 md:gap-6 md:px-8 md:py-3">
        <button
          type="button"
          className="-ml-2 flex h-11 w-11 flex-none items-center justify-center md:hidden"
          aria-label={menuOpen ? 'Close menu' : 'Open menu'}
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          {menuOpen ? <X size={26} strokeWidth={1.5} /> : <Menu size={26} strokeWidth={1.5} />}
        </button>

        <div
          className="flex flex-none cursor-pointer items-center gap-2"
          role="link"
          tabIndex={0}
          onClick={() => goTo('/')}
          onKeyDown={onEnterKey(() => goTo('/'))}
        >
          <span className="stamp border-paper-50 text-paper-50" style={{ mixBlendMode: 'normal' }}>
            MC
          </span>
          <div className="leading-none">
            <div className="h text-2xl uppercase leading-none tracking-[.03em] md:text-[40px]">{STORE_NAME}</div>
            <div className="hidden font-mono text-[15px] uppercase tracking-[.16em] text-accent-300 md:block">
              General Merchandise Manifest
            </div>
          </div>
        </div>

        <form onSubmit={onSubmit} className="hidden flex-1 border border-accent-600 bg-paper-50 md:flex">
          <label className="sr-only" htmlFor="dept-select">
            Department
          </label>
          <select
            id="dept-select"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="border-0 border-r border-divider bg-paper-200 px-3 py-3 font-mono text-lg uppercase tracking-wide text-foreground"
          >
            {categories.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Track a product, brand or SKU"
            aria-label="Search products, brands and categories"
            className="flex-1 border-0 bg-transparent px-4 py-3 text-lg text-foreground placeholder:text-paper-500"
          />
          <Button type="submit" variant="primary" className="rounded-none border-0 px-6 text-lg">
            <Search size={22} strokeWidth={1.5} />
          </Button>
        </form>

        <div className="ml-auto flex flex-none items-center gap-1 md:hidden">
          <button
            type="button"
            className="flex h-11 w-11 items-center justify-center"
            aria-label={user ? 'Account' : 'Sign in'}
            onClick={() => goTo(user ? '/orders' : '/signin')}
          >
            <User size={26} strokeWidth={1.5} />
          </button>
          <button
            type="button"
            className="relative flex h-11 w-11 items-center justify-center"
            aria-label={`Cart, ${itemCount} item${itemCount === 1 ? '' : 's'}`}
            onClick={() => goTo('/cart')}
          >
            <ShoppingCart size={26} strokeWidth={1.5} />
            {itemCount > 0 && (
              <span className="h absolute right-0.5 top-0.5 flex h-4 min-w-4 items-center justify-center bg-accent-400 px-1 text-[13px] leading-none text-accent-900">
                {itemCount}
              </span>
            )}
          </button>
        </div>

        <div className="hidden flex-none items-center gap-4 text-sm md:flex">
          <div className="flex flex-col">
            <div
              className="cursor-pointer"
              role="link"
              tabIndex={0}
              onClick={() => goTo(user ? '/orders' : '/signin')}
              onKeyDown={onEnterKey(() => goTo(user ? '/orders' : '/signin'))}
            >
              <div className="text-accent-400">{user ? `Hello, ${user.name}` : 'Hello, sign in'}</div>
              <div className="h text-[19px]">Account &amp; Lists</div>
            </div>
            {user && (
              <button
                type="button"
                className="mt-0.5 self-start font-mono text-[13px] uppercase tracking-[.16em] text-accent-300 hover:text-paper-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-400"
                onClick={handleSignOut}
              >
                Sign out
              </button>
            )}
          </div>
          <div
            className="cursor-pointer"
            role="link"
            tabIndex={0}
            onClick={() => goTo('/orders')}
            onKeyDown={onEnterKey(() => goTo('/orders'))}
          >
            <div className="text-accent-400">Returns</div>
            <div className="h text-[19px]">&amp; Orders</div>
          </div>
          <div
            className="relative flex cursor-pointer items-center gap-2 border border-accent-700 px-4 py-2.5"
            role="link"
            tabIndex={0}
            aria-label={`Cart, ${itemCount} item${itemCount === 1 ? '' : 's'}`}
            onClick={() => goTo('/cart')}
            onKeyDown={onEnterKey(() => goTo('/cart'))}
          >
            <ShoppingCart size={24} strokeWidth={1.5} />
            <span className="h text-xl text-accent-400">{itemCount}</span>
          </div>
        </div>
      </div>

      <div className="px-4 pb-3 md:hidden">
        <form onSubmit={onSubmit} className="flex border border-accent-600 bg-paper-50">
          <label className="sr-only" htmlFor="dept-select-mobile">
            Department
          </label>
          <select
            id="dept-select-mobile"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="max-w-[38%] border-0 border-r border-divider bg-paper-200 px-2 font-mono text-base uppercase text-foreground"
          >
            {categories.map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search products"
            className="min-h-12 flex-1 border-0 bg-transparent px-2.5 text-base text-foreground placeholder:text-paper-500"
          />
          <Button type="submit" variant="primary" className="min-h-11 rounded-none border-0 px-4">
            <Search size={20} strokeWidth={1.5} />
          </Button>
        </form>
      </div>

      <div className="hidden border-t border-accent-700 bg-accent-800 md:block">
        <div className="flex w-full flex-wrap items-center px-8">
          <button className="navlink" onClick={() => goDepartment('All')}>
            All departments
          </button>
          {departmentLinks.map((name) => (
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
        <nav
          aria-label="Mobile menu"
          className="absolute inset-x-0 top-full z-30 flex flex-col border-t border-accent-700 bg-accent-800 shadow-ds-lg md:hidden"
        >
          <button className="navlink min-h-11 text-left" onClick={() => goDepartment('All')}>
            All departments
          </button>
          {departmentLinks.map((name) => (
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
          {user && (
            <>
              <div className="mx-3 my-1 h-px bg-accent-700" />
              <button type="button" className="navlink min-h-11 text-left" onClick={handleSignOut}>
                Sign out
              </button>
            </>
          )}
        </nav>
      )}
    </header>
  )
}
