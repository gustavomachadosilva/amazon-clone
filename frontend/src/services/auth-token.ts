export const AUTH_STORAGE_KEY = 'mercatto:auth'

interface StoredAuth {
  token?: string
  tokenExpiresAt?: string
}

function readStoredAuth(): StoredAuth | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) return null
    return JSON.parse(raw) as StoredAuth | null
  } catch {
    return null
  }
}

// The stored token regardless of expiry — i.e. "is there a session on this device?".
export function readStoredSessionToken(): string | null {
  return readStoredAuth()?.token ?? null
}

export function readStoredToken(): string | null {
  const stored = readStoredAuth()
  if (!stored?.token) return null
  if (stored.tokenExpiresAt && new Date(stored.tokenExpiresAt).getTime() <= Date.now()) return null

  return stored.token
}
