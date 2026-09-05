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
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
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
  checkout: (buyerId: number, items: CheckoutItem[]) =>
    api.post<Order>('/api/orders/checkout', { buyerId, items }),
  getById: (id: number) => api.get<Order>(`/api/orders/${id}`),
  listByBuyer: (buyerId: number) => api.get<Order[]>(`/api/orders?buyerId=${buyerId}`),
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

export const usersApi = {
  login: (email: string, password: string) => api.post<UserResponse>('/api/users/login', { email, password }),
  register: (payload: RegisterPayload) => api.post<UserResponse>('/api/users/register', payload),
}
