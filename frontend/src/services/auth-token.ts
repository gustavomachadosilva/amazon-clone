export const AUTH_STORAGE_KEY = 'mercatto:auth'

interface StoredAuth {
  token?: string
  tokenExpiresAt?: string
}

export function readStoredToken(): string | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return null

    const stored = JSON.parse(raw) as StoredAuth | null
    if (!stored?.token) return null
    if (stored.tokenExpiresAt && new Date(stored.tokenExpiresAt).getTime() <= Date.now()) return null

    return stored.token
  } catch {
    return null
  }
}
