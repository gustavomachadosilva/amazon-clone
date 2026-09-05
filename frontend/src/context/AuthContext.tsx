import { createContext, useContext, type ReactNode } from 'react'
import { useLocalStorage } from '../hooks/useLocalStorage'
import { idFromEmail } from '../lib/auth'
import type { AuthUser } from '../types/domain'

interface AuthContextValue {
  user: AuthUser | null
  signIn: (email: string, password: string) => string | null
  register: (name: string, email: string, password: string) => string | null
  signOut: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function capitalize(word: string): string {
  return word.charAt(0).toUpperCase() + word.slice(1)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useLocalStorage<AuthUser | null>('mercatto:auth', null)

  function validate(email: string, password: string, name?: string): string | null {
    if (!email.includes('@')) return 'Enter a valid email address.'
    if (password.length < 6) return 'Password must be at least 6 characters.'
    if (name !== undefined && !name.trim()) return 'Enter your name.'
    return null
  }

  function signIn(email: string, password: string): string | null {
    const error = validate(email, password)
    if (error) return error
    setUser({ id: idFromEmail(email), name: capitalize(email.split('@')[0]), email })
    return null
  }

  function register(name: string, email: string, password: string): string | null {
    const error = validate(email, password, name)
    if (error) return error
    setUser({ id: idFromEmail(email), name: name.trim(), email })
    return null
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
