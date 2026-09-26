import { useEffect, useState, type ComponentType } from 'react'
import { Link } from 'react-router-dom'
import { Package, ShieldCheck, Store, type LucideProps } from 'lucide-react'
import AccountListsSection from '../components/account/AccountListsSection'
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
  { title: 'Login & security', description: 'Edit name, email and password', icon: ShieldCheck, to: '/account/security' },
  { title: 'Seller Central', description: 'Manage your products, orders and metrics', icon: Store, to: '/seller', onlyFor: 'SELLER' },
]

type ProfileStatus = 'loading' | 'ok' | 'error'

const ROLE_LABEL_BASE =
  'inline-flex items-center self-start rounded-ds-sm border px-2.5 py-1 font-mono text-[13.5px] uppercase tracking-[0.07em]'

// A solid label instead of the `.tag` ticket: the neutral tag is white-on-white on a card and reads as a disabled button.
function RoleLabel({ role }: { role: UserRole }) {
  return role === 'SELLER' ? (
    <span className={`${ROLE_LABEL_BASE} border-accent-600 bg-accent-600 text-paper-50`}>Seller</span>
  ) : (
    <span className={`${ROLE_LABEL_BASE} border-paper-500 bg-paper-200 text-paper-800`}>Buyer</span>
  )
}

interface ProfileCardProps {
  status: ProfileStatus
  profile: UserProfile | null
  onRetry: () => void
  onSignOut: () => void
}

// One frame for every state (loading / error / ok) so the page doesn't jump when the profile arrives.
function ProfileCard({ status, profile, onRetry, onSignOut }: ProfileCardProps) {
  return (
    <Blueprint
      as="section"
      aria-label="Profile"
      corners
      className="flex min-h-[300px] flex-col gap-4 bg-card p-4 md:p-6 lg:sticky lg:top-4"
    >
      <div className="min-w-0 flex-1">
        {status === 'loading' && (
          <div role="status">
            <span className="sr-only">Loading your account…</span>
            <div aria-hidden="true" className="animate-pulse space-y-3">
              <div className="h-7 w-20 rounded-ds-sm bg-paper-200" />
              <div className="h-7 w-3/4 rounded-ds-sm bg-paper-200" />
              <div className="h-5 w-full rounded-ds-sm bg-paper-200" />
              <div className="h-5 w-1/2 rounded-ds-sm bg-paper-200" />
            </div>
          </div>
        )}

        {status === 'error' && (
          <div role="alert" className="callout-alert flex-col items-start">
            <span>We couldn't load your account details.</span>
            <Button variant="secondary" onClick={onRetry}>
              Try again
            </Button>
          </div>
        )}

        {status === 'ok' && profile && (
          <div className="flex flex-col">
            <RoleLabel role={profile.role} />
            <h2 className="card-title mb-1 mt-3 break-words">{profile.name}</h2>
            <p className="break-all font-mono text-[16px] text-paper-700">{profile.email}</p>
            <p className="card-meta mt-3">Member since {formatMemberSince(profile.createdAt)}</p>
          </div>
        )}
      </div>

      <div className="border-t border-divider pt-4">
        <Button variant="secondary" block className="mt-0" onClick={onSignOut}>
          Sign out
        </Button>
      </div>
    </Blueprint>
  )
}

interface SectionShellProps {
  id: string
  title: string
  description: string
  linkLabel: string
  to: string
}

// Structural shell for the account-information section; its real content lands in #211.
function SectionShell({ id, title, description, linkLabel, to }: SectionShellProps) {
  return (
    <Blueprint as="section" aria-labelledby={id} className="flex flex-col bg-card p-4 md:p-6">
      <h2 id={id} className="card-title mb-2">
        {title}
      </h2>
      <p className="text-[16px] text-paper-700">{description}</p>
      <Link to={to} className="btn btn-secondary mt-4 self-start">
        {linkLabel}
      </Link>
    </Blueprint>
  )
}

function ShortcutTile({ title, description, icon: Icon, to }: Shortcut) {
  return (
    <Link
      to={to}
      className="block h-full focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent-600"
    >
      <Blueprint corners className="prod flex h-full flex-col gap-2 bg-card p-4">
        <div className="flex items-center gap-3">
          <Icon size={24} strokeWidth={1.5} aria-hidden="true" className="flex-none text-accent-700" />
          <h3 className="card-title mb-0 text-xl">{title}</h3>
        </div>
        <p className="text-[16px] text-paper-700">{description}</p>
      </Blueprint>
    </Link>
  )
}

export default function Account() {
  const { user } = useAuth()
  const signOut = useSignOut()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [status, setStatus] = useState<ProfileStatus>('loading')
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

      <div className="mt-4 grid grid-cols-1 items-start gap-6 lg:grid-cols-[320px_minmax(0,1fr)]">
        <ProfileCard status={status} profile={profile} onRetry={retry} onSignOut={signOut} />

        <div className="flex min-w-0 flex-col gap-6">
          <SectionShell
            id="account-info-heading"
            title="Your account information"
            description="Update your name, email and password."
            linkLabel="Edit account details"
            to="/account/security"
          />
          <AccountListsSection />

          <section aria-labelledby="shortcuts-heading">
            <h2 id="shortcuts-heading" className="card-title mb-3">
              Shortcuts
            </h2>
            {/* auto-fit: every role fills the row (2 or 3 columns on desktop, 1 on a phone) — no hole. */}
            <ul
              aria-label="Account shortcuts"
              className="grid grid-cols-[repeat(auto-fit,minmax(min(200px,100%),1fr))] gap-4"
            >
              {shortcuts.map((shortcut) => (
                <li key={shortcut.title} className="h-full">
                  <ShortcutTile {...shortcut} />
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>
    </div>
  )
}
