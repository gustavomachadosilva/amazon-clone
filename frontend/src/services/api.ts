const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })

  if (!response.ok) {
    throw new Error(`Request to ${path} failed with status ${response.status}`)
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

interface Page<T> {
  content: T[]
}

export const catalogApi = {
  search: (query?: string, category?: string) => {
    const params = new URLSearchParams()
    if (query) params.set('query', query)
    if (category) params.set('category', category)
    return api.get<Page<Product>>(`/api/catalog/products?${params.toString()}`)
  },
  getById: (id: number) => api.get<Product>(`/api/catalog/products/${id}`),
}

export const sellersApi = {
  getInventory: (sellerId: number) => api.get<Page<Product>>(`/api/sellers/${sellerId}/products`),
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
