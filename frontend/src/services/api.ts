import { notifyUnauthorized } from './auth-events'
import { readStoredSessionToken, readStoredToken } from './auth-token'
import type { UserRole } from '../types/domain'

const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

// Resolves a backend path (e.g. a review media URL like `/api/reviews/media/7`) against the API
// base, since the frontend has no dev proxy. Absolute http(s) and blob: URLs pass through.
export function resolveApiUrl(path: string): string {
  if (/^(https?:|blob:)/i.test(path)) return path
  const base = API_BASE_URL.replace(/\/+$/, '')
  return `${base}${path.startsWith('/') ? path : `/${path}`}`
}

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
  // A FormData body must not get a fixed Content-Type: the browser sets multipart/form-data
  // together with the boundary it generated.
  const isForm = options.body instanceof FormData
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      ...(isForm ? {} : { 'Content-Type': 'application/json' }),
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
  patch: <T,>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: <T,>(path: string) => request<T>(path, { method: 'DELETE' }),
  postForm: <T,>(path: string, form: FormData) => request<T>(path, { method: 'POST', body: form }),
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

/** Orderings accepted by the backend's `?sort=` on the product search. */
export type ProductSort = 'relevance' | 'price_asc' | 'price_desc' | 'rating'

/** Product search filters, all applied server-side so totals/pagination match the results. */
export interface ProductSearchParams {
  query?: string
  category?: string
  minPrice?: number
  maxPrice?: number
  minRating?: number
  sort?: ProductSort
  page?: number
  size?: number
}

export const catalogApi = {
  search: ({ query, category, minPrice, maxPrice, minRating, sort, page = 0, size = 10 }: ProductSearchParams = {}) => {
    const params = new URLSearchParams()
    if (query) params.set('query', query)
    if (category) params.set('category', category)
    if (minPrice !== undefined) params.set('minPrice', minPrice.toString())
    if (maxPrice !== undefined) params.set('maxPrice', maxPrice.toString())
    if (minRating !== undefined) params.set('minRating', minRating.toString())
    if (sort && sort !== 'relevance') params.set('sort', sort)
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
  fulfillmentStatus: FulfillmentStatus
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
  advanceFulfillment: (sellerId: number, orderId: number, status: FulfillmentStatus) =>
    api.post<SellerOrder>(`/api/sellers/${sellerId}/orders/${orderId}/fulfillment`, { status }),
}

export type OrderStatus = 'PENDING' | 'PROCESSING' | 'PAID' | 'FAILED' | 'CANCELLED'

export type FulfillmentStatus = 'NOT_SHIPPED' | 'SHIPPED' | 'OUT_FOR_DELIVERY' | 'DELIVERED'

export interface OrderItem {
  id: number
  productId: number
  // Null only on legacy items whose seller could not be backfilled.
  sellerId: number | null
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
  fulfillmentStatus: FulfillmentStatus
  // ISO instants; each is null until the order reaches that fulfillment step.
  shippedAt: string | null
  outForDeliveryAt: string | null
  deliveredAt: string | null
  // ISO date (YYYY-MM-DD), computed by the backend from createdAt + shippingMethod.
  estimatedDeliveryDate: string | null
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
  // Only allowed while the order hasn't shipped; the backend answers 409 otherwise.
  updateAddress: (id: number, address: OrderAddress) =>
    api.patch<Order>(`/api/orders/${id}/address`, address),
  // Only for FAILED orders. A second decline still answers 200 with the order left FAILED; a 409
  // means an item went out of stock (see isOutOfStockError) or the order isn't retryable anymore.
  retryPayment: (id: number, paymentMethod: PaymentMethod) =>
    api.post<Order>(`/api/orders/${id}/payment`, { paymentMethod }),
}

// The 409s a payment retry can get share a status, so the out-of-stock case is told apart by the
// messages from the stock check OrderServiceImpl runs before retrying.
export function isOutOfStockError(e: unknown): boolean {
  return (
    e instanceof ApiRequestError &&
    e.status === 409 &&
    /insufficient stock|no longer available/i.test(e.apiMessage ?? '')
  )
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

// Omitted fields are left unchanged by the backend.
export interface UpdateProfilePayload {
  name?: string
  email?: string
}

export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
}

export const usersApi = {
  login: (email: string, password: string) => api.post<LoginResponse>('/api/users/login', { email, password }),
  register: (payload: RegisterPayload) => api.post<UserResponse>('/api/users/register', payload),
  me: () => api.get<UserProfile>('/api/users/me'),
  updateMe: (payload: UpdateProfilePayload) => api.patch<UserProfile>('/api/users/me', payload),
  changePassword: (payload: ChangePasswordPayload) => api.put<void>('/api/users/me/password', payload),
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

export type ReviewMediaType = 'IMAGE' | 'VIDEO'

export interface ReviewMedia {
  id: number
  type: ReviewMediaType
  // Relative to the API (`/api/reviews/media/{id}`) — pass it through resolveApiUrl before use.
  url: string
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
  media: ReviewMedia[]
}

export interface CreateReviewPayload {
  stars: number
  title: string
  text: string
}

export const reviewsApi = {
  listByProduct: (productId: number) => api.get<ReviewView[]>(`/api/reviews/products/${productId}`),
  // With files, the backend's multipart variant takes a JSON `review` part plus repeated `files`.
  create: (productId: number, payload: CreateReviewPayload, files: File[] = []) => {
    const path = `/api/reviews/products/${productId}`
    if (files.length === 0) return api.post<ReviewView>(path, payload)
    const form = new FormData()
    form.append('review', new Blob([JSON.stringify(payload)], { type: 'application/json' }))
    files.forEach((file) => form.append('files', file, file.name))
    return api.postForm<ReviewView>(path, form)
  },
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
