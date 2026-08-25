export function idFromEmail(email: string): number {
  let hash = 0
  for (let i = 0; i < email.length; i++) hash = (hash * 31 + email.charCodeAt(i)) | 0
  return Math.abs(hash) || 1
}
