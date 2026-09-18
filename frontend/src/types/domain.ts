export interface CartLine {
  productId: number
  name: string
  price: number
  qty: number
}

export interface WishList {
  id: string
  name: string
  items: number[]
}

export type UserRole = 'BUYER' | 'SELLER'

export interface AuthUser {
  id: number
  name: string
  email: string
  role: UserRole
  token?: string
  tokenExpiresAt?: string
}
