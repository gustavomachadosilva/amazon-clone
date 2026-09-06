import { readStoredToken } from './auth-token'
import type { UserRole } from '../types/domain'

const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

interface ApiErrorBody {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
}

export class ApiRequestError extends Error {
  status: number
  apiMessage?: string

  constructor(status: number, apiMessage?: string) {
    super(apiMessage ?? `Request failed with status ${status}`)
    this.name = 'ApiRequestError'
    this.status = status
    this.apiMessage = apiMessage
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = readStoredToken()
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
    ...options,
  })

  if (!response.ok) {
    let apiMessage: string | undefined
    try {
      const body = (await response.json()) as ApiErrorBody
      apiMessage = body?.message
    } catch {
      // Body empty or not JSON (e.g. login 401 returns an empty body) — fall back to no message.
    }
    throw new ApiRequestError(response.status, apiMessage)
  }

  return response.json() as Promise<T>
}

export const api = {
  get: <T,>(path: string) => request<T>(path),
  post: <T,>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T,>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T,>(path: string) => request<T>(path, { method: 'DELETE' }),
}

export interface Product {
  id: number
  name: string
  description: string
  price: number
  stockQuantity: number
  category: string
  sellerId: number
  imageUrl?: string
}

export interface Page<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
  first: boolean
  last: boolean
  empty: boolean
}

export const catalogApi = {
  search: (query?: string, category?: string, page: number = 0, size: number = 10) => {
    const params = new URLSearchParams()
    if (query) params.set('query', query)
    if (category) params.set('category', category)
    params.set('page', page.toString())
    params.set('size', size.toString())
    return api.get<Page<Product>>(`/api/catalog/products?${params.toString()}`)
  },
  getById: (id: number) => api.get<Product>(`/api/catalog/products/${id}`),
}

export const sellersApi = {
  getInventory: (sellerId: number, page: number = 0, size: number = 10) =>
    api.get<Page<Product>>(`/api/sellers/${sellerId}/products?page=${page}&size=${size}`),
}

export type OrderStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED'

export interface OrderItem {
  id: number
  productId: number
  quantity: number
  unitPrice: number
}

export interface Order {
  id: number
  buyerId: number
  status: OrderStatus
  totalAmount: number
  items: OrderItem[]
  createdAt: string
}

export interface CheckoutItem {
  productId: number
  quantity: number
}

export const ordersApi = {
  checkout: (items: CheckoutItem[]) => api.post<Order>('/api/orders/checkout', { items }),
  getById: (id: number) => api.get<Order>(`/api/orders/${id}`),
  listByBuyer: () => api.get<Order[]>('/api/orders'),
}

export interface RegisterPayload {
  name: string
  email: string
  password: string
  role: UserRole
}

export interface UserResponse {
  id: number
  name: string
  email: string
  role: UserRole
}

export interface LoginResponse extends UserResponse {
  token: string
  expiresAt: string
}

export const usersApi = {
  login: (email: string, password: string) => api.post<LoginResponse>('/api/users/login', { email, password }),
  register: (payload: RegisterPayload) => api.post<UserResponse>('/api/users/register', payload),
}

export interface CartItemView {
  productId: number
  productName: string
  unitPrice: number
  quantity: number
  lineTotal: number
  savedForLater: boolean
}

export interface CartView {
  userId: number
  items: CartItemView[]
  savedForLater: CartItemView[]
  itemCount: number
  total: number
}

export const cartApi = {
  get: (userId: number) => api.get<CartView>(`/api/cart/${userId}`),
  addItem: (userId: number, productId: number, quantity: number) =>
    api.post<CartView>(`/api/cart/${userId}/items`, { productId, quantity }),
  updateQuantity: (userId: number, productId: number, quantity: number) =>
    api.put<CartView>(`/api/cart/${userId}/items/${productId}`, { quantity }),
  removeItem: (userId: number, productId: number) =>
    api.delete<CartView>(`/api/cart/${userId}/items/${productId}`),
  saveForLater: (userId: number, productId: number) =>
    api.post<CartView>(`/api/cart/${userId}/items/${productId}/save-for-later`, undefined),
  moveToCart: (userId: number, productId: number) =>
    api.post<CartView>(`/api/cart/${userId}/items/${productId}/move-to-cart`, undefined),
  clear: (userId: number) => api.delete<CartView>(`/api/cart/${userId}`),
}
