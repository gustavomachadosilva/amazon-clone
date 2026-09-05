import { createContext, useContext, type ReactNode } from 'react'
import { useLocalStorage } from '../hooks/useLocalStorage'
import { ApiRequestError, usersApi } from '../services/api'
import type { AuthUser, UserRole } from '../types/domain'

interface AuthContextValue {
  user: AuthUser | null
  signIn: (email: string, password: string) => Promise<string | null>
  register: (name: string, email: string, password: string, role: UserRole) => Promise<string | null>
  signOut: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useLocalStorage<AuthUser | null>('mercatto:auth', null)

  async function signIn(email: string, password: string): Promise<string | null> {
    if (!email.includes('@')) return 'Enter a valid email address.'

    try {
      const { token, expiresAt, ...user } = await usersApi.login(email, password)
      setUser({ ...user, token, tokenExpiresAt: expiresAt })
      return null
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) {
        return 'Incorrect email or password.'
      }
      return 'Could not sign in. Please try again.'
    }
  }

  async function register(name: string, email: string, password: string, role: UserRole): Promise<string | null> {
    if (!email.includes('@')) return 'Enter a valid email address.'
    if (!name.trim()) return 'Enter your name.'
    if (password.length < 8) return 'Password must be at least 8 characters.'

    try {
      const user = await usersApi.register({ name: name.trim(), email, password, role })
      setUser(user)
      return null
    } catch (e) {
      if (e instanceof ApiRequestError) {
        if (e.status === 409) return 'This email is already registered.'
        if (e.status === 400) return e.apiMessage ?? 'Please check the information provided.'
      }
      return 'Could not create your account. Please try again.'
    }
  }

  function signOut() {
    setUser(null)
  }

  return <AuthContext.Provider value={{ user, signIn, register, signOut }}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- hook colocalizado com o Provider; separar em arquivo próprio é refatoração fora do escopo deste card
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
