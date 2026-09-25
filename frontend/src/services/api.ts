import { notifyUnauthorized } from './auth-events'
import { readStoredSessionToken, readStoredToken } from './auth-token'
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

function isLoginRequest(path: string, method?: string): boolean {
  return method === 'POST' && path === '/api/users/login'
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  // An expired token isn't sent at all, but the request still counts as authenticated: the
  // user thinks they're signed in, so a 401 must end that session too.
  const sessionToken = readStoredSessionToken()
  const token = readStoredToken()
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (!response.ok) {
    let apiMessage: string | undefined
    try {
      const body = (await response.json()) as ApiErrorBody
      apiMessage = body?.message
    } catch {
      // Body empty or not JSON (e.g. login 401 returns an empty body) — fall back to no message.
    }
    // Skip login (a 401 there just means wrong credentials) and responses for a session that
    // was already replaced meanwhile (e.g. the user signed in again while this was in flight).
    if (
      response.status === 401 &&
      sessionToken &&
      !isLoginRequest(path, options.method) &&
      readStoredSessionToken() === sessionToken
    ) {
      notifyUnauthorized(sessionToken)
    }
    throw new ApiRequestError(response.status, apiMessage)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export const api = {
  get: <T,>(path: string) => request<T>(path),
  post: <T,>(path: string, body: unknown, headers?: HeadersInit) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body), headers }),
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
  brand: string | null
  warrantyMonths: number | null
  modelNumber: string | null
  listPrice: number | null
  averageRating: number
  reviewCount: number
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

export interface ProductInput {
  name: string
  description?: string
  price: number
  stockQuantity: number
  category: string
  imageUrl?: string
  brand?: string
  warrantyMonths?: number
  modelNumber?: string
  listPrice?: number
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
  getCategories: () => api.get<string[]>('/api/catalog/categories'),
  create: (input: ProductInput) => api.post<Product>('/api/catalog/products', input),
  update: (id: number, input: ProductInput) => api.put<Product>(`/api/catalog/products/${id}`, input),
  remove: (id: number) => api.delete<void>(`/api/catalog/products/${id}`),
}

export interface SellerOrderItem {
  productId: number
  quantity: number
  unitPrice: number
}

export interface SellerOrder {
  orderId: number
  buyerId: number
  status: OrderStatus
  createdAt: string
  items: SellerOrderItem[]
  subtotal: number
}

export interface SellerMetrics {
  totalRevenue: number
  lowStockProducts: Product[]
}

export const sellersApi = {
  getInventory: (sellerId: number, page: number = 0, size: number = 10) =>
    api.get<Page<Product>>(`/api/sellers/${sellerId}/products?page=${page}&size=${size}`),
  getOrders: (sellerId: number) => api.get<SellerOrder[]>(`/api/sellers/${sellerId}/orders`),
  getMetrics: (sellerId: number) => api.get<SellerMetrics>(`/api/sellers/${sellerId}/metrics`),
}

export type OrderStatus = 'PENDING' | 'PROCESSING' | 'PAID' | 'FAILED' | 'CANCELLED'

export interface OrderItem {
  id: number
  productId: number
  quantity: number
  unitPrice: number
}

export interface OrderAddress {
  fullName: string
  street: string
  city: string
  state: string
  zip: string
}

export type ShippingMethod = 'STANDARD' | 'EXPRESS' | 'PICKUP'
export type PaymentMethod = 'CARD' | 'STORE' | 'GIFT'

export interface Order {
  id: number
  buyerId: number
  status: OrderStatus
  totalAmount: number
  items: OrderItem[]
  // Orders placed before this field existed have none of these set.
  address: OrderAddress | null
  shippingMethod: ShippingMethod | null
  paymentMethod: PaymentMethod | null
  createdAt: string
}

export interface CheckoutItem {
  productId: number
  quantity: number
}

export interface CheckoutPayload {
  items: CheckoutItem[]
  address: OrderAddress
  shippingMethod: ShippingMethod
  paymentMethod: PaymentMethod
}

export const ordersApi = {
  checkout: (payload: CheckoutPayload, idempotencyKey?: string) =>
    api.post<Order>(
      '/api/orders/checkout',
      payload,
      idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    ),
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

// GET /api/users/me also returns when the account was created; login/register responses don't,
// so it lives in its own type instead of on the shared UserResponse.
export interface UserProfile extends UserResponse {
  createdAt: string
}

export interface LoginResponse extends UserResponse {
  token: string
  expiresAt: string
}

export const usersApi = {
  login: (email: string, password: string) => api.post<LoginResponse>('/api/users/login', { email, password }),
  register: (payload: RegisterPayload) => api.post<UserResponse>('/api/users/register', payload),
  me: () => api.get<UserProfile>('/api/users/me'),
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

export interface ReviewView {
  id: number
  productId: number
  authorId: number
  authorName: string
  stars: number
  title: string
  text: string
  helpfulCount: number
  createdAt: string
}

export interface CreateReviewPayload {
  stars: number
  title: string
  text: string
}

export const reviewsApi = {
  listByProduct: (productId: number) => api.get<ReviewView[]>(`/api/reviews/products/${productId}`),
  create: (productId: number, payload: CreateReviewPayload) =>
    api.post<ReviewView>(`/api/reviews/products/${productId}`, payload),
  markHelpful: (reviewId: number) => api.post<ReviewView>(`/api/reviews/${reviewId}/helpful`, undefined),
}

export interface WishListView {
  id: number
  buyerId: number
  name: string
  productIds: number[]
  createdAt: string
}

export interface AddItemResult {
  list: WishListView
  alreadyPresent: boolean
}

export const listsApi = {
  listMine: () => api.get<WishListView[]>('/api/lists'),
  create: (name: string) => api.post<WishListView>('/api/lists', { name }),
  addItem: (listId: number, productId: number) =>
    api.post<AddItemResult>(`/api/lists/${listId}/items`, { productId }),
  removeItem: (listId: number, productId: number) =>
    api.delete<WishListView>(`/api/lists/${listId}/items/${productId}`),
  remove: (listId: number) => api.delete<void>(`/api/lists/${listId}`),
}
