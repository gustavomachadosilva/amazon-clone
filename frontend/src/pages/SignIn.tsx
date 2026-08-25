import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Blueprint from '../components/ui/Blueprint'
import { useAuth } from '../context/AuthContext'
import { STORE_NAME } from '../lib/constants'

export default function SignIn() {
  const navigate = useNavigate()
  const auth = useAuth()
  const [mode, setMode] = useState<'signin' | 'register'>('signin')
  const [form, setForm] = useState({ name: '', email: '', pass: '' })
  const [error, setError] = useState<string | null>(null)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const result =
      mode === 'signin'
        ? auth.signIn(form.email, form.pass)
        : auth.register(form.name, form.email, form.pass)
    if (result) {
      setError(result)
      return
    }
    navigate('/')
  }

  return (
    <div style={{ maxWidth: 400, margin: '40px auto', padding: 24 }}>
      <Blueprint style={{ padding: 24 }}>
        <h1 style={{ fontSize: 24 }}>{mode === 'signin' ? 'Sign in' : 'Create account'}</h1>
        <p style={{ fontSize: 13, color: '#5d5d60' }}>
          {mode === 'signin' ? 'Use your email and password to continue.' : 'One account for orders, lists and reviews.'}
        </p>

        <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 12 }}>
          {mode === 'register' && (
            <div className="field">
              <label>Your name</label>
              <input className="input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
          )}
          <div className="field">
            <label>Email</label>
            <input
              className="input"
              type="email"
              placeholder="you@email.com"
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </div>
          <div className="field">
            <label>Password</label>
            <input
              className="input"
              type="password"
              value={form.pass}
              onChange={(e) => setForm({ ...form, pass: e.target.value })}
            />
          </div>

          {error && (
            <div style={{ fontSize: 12.5, color: 'var(--color-accent-800)', borderLeft: '2px solid var(--color-accent-800)', paddingLeft: 8 }}>
              {error}
            </div>
          )}

          <button className="btn btn-primary btn-block" type="submit">
            {mode === 'signin' ? 'Continue' : 'Create your account'}
          </button>
        </form>

        <p style={{ fontSize: 11.5, color: '#7a7a7d', marginTop: 12 }}>
          By continuing you agree to the terms of this academic prototype.
        </p>
        <div className="hr" />
        <button
          className="btn btn-ghost"
          onClick={() => {
            setMode(mode === 'signin' ? 'register' : 'signin')
            setError(null)
          }}
        >
          {mode === 'signin' ? `New to ${STORE_NAME}? Create an account` : 'Already have an account? Sign in'}
        </button>
      </Blueprint>
    </div>
  )
}
