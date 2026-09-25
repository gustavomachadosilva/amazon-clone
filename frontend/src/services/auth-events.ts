// Tiny pub/sub so api.ts can report a rejected session without importing AuthContext
// (AuthContext already imports api.ts, so a direct import would be circular).
type UnauthorizedListener = (rejectedToken: string) => void

const listeners = new Set<UnauthorizedListener>()

export function onUnauthorized(listener: UnauthorizedListener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function notifyUnauthorized(rejectedToken: string): void {
  listeners.forEach((listener) => listener(rejectedToken))
}
