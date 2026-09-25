import { useState } from 'react'
import { useLocation, useNavigate, type Location } from 'react-router-dom'
import { Blueprint, Button, Input, Select } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { STORE_NAME } from '../lib/constants'
import type { UserRole } from '../types/domain'

// Where to go after signing in: the page a protected route (RequireAuth) bounced the visitor
// from, or home when they came here directly.
function redirectTarget(state: unknown): string {
  const from = (state as { from?: Partial<Location> } | null)?.from
  if (!from?.pathname || from.pathname === '/signin') return '/'
  return `${from.pathname}${from.search ?? ''}${from.hash ?? ''}`
}

export default function SignIn() {
  const navigate = useNavigate()
  const location = useLocation()
  const auth = useAuth()
  const [mode, setMode] = useState<'signin' | 'register'>('signin')
  const [form, setForm] = useState({ name: '', email: '', pass: '', role: 'BUYER' as UserRole })
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setIsSubmitting(true)
    try {
      const result =
        mode === 'signin'
          ? await auth.signIn(form.email, form.pass)
          : await auth.register(form.name, form.email, form.pass, form.role)
      if (result) {
        setError(result)
        return
      }
      navigate(redirectTarget(location.state), { replace: true })
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-[460px] px-4 py-6 md:py-10">
      <Blueprint className="p-5 md:p-6">
        <h1 className="text-2xl">{mode === 'signin' ? 'Sign in' : 'Create account'}</h1>
        <p className="text-[16.5px] text-paper-700">
          {mode === 'signin' ? 'Use your email and password to continue.' : 'One account for orders, lists and reviews.'}
        </p>

        <form onSubmit={submit} className="mt-3 flex flex-col gap-3">
          {mode === 'register' && (
            <Input
              label="Your name"
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
          )}
          <Input
            label="Email"
            type="email"
            placeholder="you@email.com"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
          />
          <Input
            label="Password"
            type="password"
            value={form.pass}
            onChange={(e) => setForm({ ...form, pass: e.target.value })}
          />
          {mode === 'register' && (
            <Select
              label="Account type"
              value={form.role}
              onChange={(e) => setForm({ ...form, role: e.target.value as UserRole })}
            >
              <option value="BUYER">I want to buy</option>
              <option value="SELLER">I want to sell</option>
            </Select>
          )}

          {error && (
            <div role="alert" className="callout-alert">
              {error}
            </div>
          )}

          <Button variant="primary" block type="submit" disabled={isSubmitting}>
            {mode === 'signin' ? 'Continue' : 'Create your account'}
          </Button>
        </form>

        <p style={{ fontSize: 11.5, color: 'var(--color-paper-600)', marginTop: 12 }}>
          By continuing you agree to the terms of this academic prototype.
        </p>
        <div className="hr" />
        <Button
          variant="ghost"
          onClick={() => {
            setMode(mode === 'signin' ? 'register' : 'signin')
            setError(null)
          }}
        >
          {mode === 'signin' ? `New to ${STORE_NAME}? Create an account` : 'Already have an account? Sign in'}
        </Button>
      </Blueprint>
    </div>
  )
}
