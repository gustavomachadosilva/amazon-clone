import { useEffect, useState, type ComponentType } from 'react'
import { Link } from 'react-router-dom'
import { ListChecks, Package, ShieldCheck, Store, type LucideProps } from 'lucide-react'
import { Blueprint, Button } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useSignOut } from '../hooks/useSignOut'
import { formatMemberSince } from '../lib/format'
import { usersApi, type UserProfile } from '../services/api'
import type { UserRole } from '../types/domain'

interface Shortcut {
  title: string
  description: string
  icon: ComponentType<LucideProps>
  to: string
  onlyFor?: UserRole
}

const SHORTCUTS: Shortcut[] = [
  { title: 'Your Orders', description: 'Track, return or buy things again', icon: Package, to: '/orders' },
  { title: 'Your Lists', description: 'View and manage your wish lists', icon: ListChecks, to: '/lists' },
  { title: 'Login & security', description: 'Edit name, email and password', icon: ShieldCheck, to: '/account/security' },
  { title: 'Seller Central', description: 'Manage your products, orders and metrics', icon: Store, to: '/seller', onlyFor: 'SELLER' },
]

function ShortcutTile({ title, description, icon: Icon, to }: Shortcut) {
  return (
    <Link to={to} className="block h-full focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent-600">
      <Blueprint corners className="prod flex h-full gap-3 bg-card p-4">
        <Icon size={28} strokeWidth={1.5} aria-hidden="true" className="flex-none text-accent-700" />
        <div className="min-w-0">
          <h2 className="card-title text-xl">{title}</h2>
          <p className="text-[16px] text-paper-700">{description}</p>
        </div>
      </Blueprint>
    </Link>
  )
}

export default function Account() {
  const { user } = useAuth()
  const signOut = useSignOut()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [status, setStatus] = useState<'loading' | 'ok' | 'error'>('loading')
  const [retryTick, setRetryTick] = useState(0)

  useEffect(() => {
    let cancelled = false
    usersApi
      .me()
      .then((data) => {
        if (cancelled) return
        setProfile(data)
        setStatus('ok')
      })
      .catch(() => {
        if (!cancelled) setStatus('error')
      })
    return () => {
      cancelled = true
    }
  }, [retryTick])

  function retry() {
    setStatus('loading')
    setRetryTick((tick) => tick + 1)
  }

  // Shortcuts depend only on the signed-in role, so they show even while the profile loads.
  const shortcuts = SHORTCUTS.filter((shortcut) => !shortcut.onlyFor || shortcut.onlyFor === user?.role)

  return (
    <div className="mx-auto max-w-[1320px] px-4 py-4 md:px-6 md:py-6">
      <h1>Your Account</h1>

      <section aria-label="Profile" className="mt-4">
        {status === 'loading' && (
          <p role="status" className="text-[16.5px] text-paper-700">
            Loading your account…
          </p>
        )}

        {status === 'error' && (
          <div role="alert" className="callout-alert flex-wrap">
            <span>We couldn't load your account details.</span>
            <Button variant="secondary" onClick={retry}>
              Try again
            </Button>
          </div>
        )}

        {status === 'ok' && profile && (
          <Blueprint corners className="flex flex-col gap-3 bg-card p-4 sm:flex-row sm:items-start sm:justify-between md:p-6">
            <div className="min-w-0">
              <h2 className="card-title break-words">{profile.name}</h2>
              <p className="min-w-0 break-all font-mono text-[16px] text-paper-700">{profile.email}</p>
              <p className="card-meta mt-2">Member since {formatMemberSince(profile.createdAt)}</p>
            </div>
            {profile.role === 'SELLER' ? (
              <span className="tag tag-accent-2 self-start">Seller</span>
            ) : (
              <span className="tag tag-neutral self-start">Buyer</span>
            )}
          </Blueprint>
        )}
      </section>

      <ul className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Account shortcuts">
        {shortcuts.map((shortcut) => (
          <li key={shortcut.title}>
            <ShortcutTile {...shortcut} />
          </li>
        ))}
      </ul>

      <div className="mt-6">
        <Button variant="secondary" onClick={signOut}>
          Sign out
        </Button>
      </div>
    </div>
  )
}
