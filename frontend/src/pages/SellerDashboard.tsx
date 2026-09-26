import { useCallback, useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import {
  ApiRequestError,
  catalogApi,
  Product,
  ProductInput,
  SellerMetrics,
  SellerOrder,
  sellersApi,
} from '../services/api'
import { Button, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui'
import ProductForm from '../components/ProductForm'
import { useAuth } from '../context/AuthContext'
import { useSignOut } from '../hooks/useSignOut'
import { usd } from '../lib/format'
import { nextFulfillmentAction, shipmentBadge } from '../lib/orderStatus'

interface Feedback {
  type: 'success' | 'error'
  message: string
}

type Tab = 'products' | 'orders'

const STATUS_STYLES: Record<SellerOrder['status'], string> = {
  PAID: 'bg-accent2-100 text-accent2-800',
  PENDING: 'bg-neutral-100 text-neutral-700',
  PROCESSING: 'bg-neutral-100 text-neutral-700',
  FAILED: 'bg-accent-100 text-accent-800',
  CANCELLED: 'bg-accent-100 text-accent-800',
}

export default function SellerDashboard() {
  const { user } = useAuth()
  const signOut = useSignOut()
  const [activeTab, setActiveTab] = useState<Tab>('products')
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<Product | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [feedback, setFeedback] = useState<Feedback | null>(null)
  const [orders, setOrders] = useState<SellerOrder[]>([])
  const [metrics, setMetrics] = useState<SellerMetrics | null>(null)
  const [ordersLoadError, setOrdersLoadError] = useState(false)
  // Per order, so finishing one update doesn't re-enable another order's button mid-request. The ref
  // is the synchronous guard (a double click lands before the re-render that disables the button);
  // the state drives the disabled attribute.
  const advancingRef = useRef(new Set<number>())
  const [advancingOrderIds, setAdvancingOrderIds] = useState<ReadonlySet<number>>(new Set())

  const fetchInventory = useCallback(() => {
    if (!user) return
    sellersApi.getInventory(user.id).then((page) => setProducts(page.content)).catch(console.error)
  }, [user])

  useEffect(() => {
    fetchInventory()
  }, [fetchInventory])

  useEffect(() => {
    catalogApi.getCategories().then(setCategories).catch(console.error)
  }, [])

  const fetchOrders = useCallback(() => {
    if (!user) return
    sellersApi
      .getOrders(user.id)
      .then((data) => {
        setOrders(data)
        setOrdersLoadError(false)
      })
      .catch(() => setOrdersLoadError(true))
    sellersApi.getMetrics(user.id).then(setMetrics).catch(console.error)
  }, [user])

  useEffect(() => {
    fetchOrders()
  }, [fetchOrders])

  function openNewProductForm() {
    setEditingProduct(null)
    setIsFormOpen(true)
  }

  function openEditForm(product: Product) {
    setEditingProduct(product)
    setIsFormOpen(true)
  }

  function closeForm() {
    setIsFormOpen(false)
    setEditingProduct(null)
  }

  async function handleSubmit(input: ProductInput) {
    setIsSubmitting(true)
    try {
      if (editingProduct) {
        await catalogApi.update(editingProduct.id, input)
        setFeedback({ type: 'success', message: 'Product updated successfully.' })
      } else {
        await catalogApi.create(input)
        setFeedback({ type: 'success', message: 'Product created successfully.' })
      }
      closeForm()
      fetchInventory()
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleDelete(product: Product) {
    if (!window.confirm(`Are you sure you want to delete "${product.name}"?`)) return

    try {
      await catalogApi.remove(product.id)
      setFeedback({ type: 'success', message: 'Product deleted successfully.' })
      fetchInventory()
    } catch {
      setFeedback({ type: 'error', message: 'Could not delete the product. Please try again.' })
    }
  }

  async function handleAdvance(order: SellerOrder) {
    const action = nextFulfillmentAction(order)
    if (!user || !action || advancingRef.current.has(order.orderId)) return

    advancingRef.current.add(order.orderId)
    setAdvancingOrderIds(new Set(advancingRef.current))
    setFeedback(null)
    try {
      const updated = await sellersApi.advanceFulfillment(user.id, order.orderId, action.next)
      setOrders((current) => current.map((o) => (o.orderId === updated.orderId ? updated : o)))
      setFeedback({
        type: 'success',
        message: `Order #${order.orderId} ${action.label.replace(/^Mark as/, 'marked as')}.`,
      })
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 409) {
        // Another tab (or a co-seller on the same order) moved it first; show the current state.
        fetchOrders()
        setFeedback({
          type: 'error',
          message: `Order #${order.orderId} changed in the meantime — the list was refreshed.`,
        })
      } else if (e instanceof ApiRequestError && e.status === 403) {
        setFeedback({ type: 'error', message: "You can't update this order." })
      } else {
        setFeedback({ type: 'error', message: 'Could not update shipping status. Please try again.' })
      }
    } finally {
      advancingRef.current.delete(order.orderId)
      setAdvancingOrderIds(new Set(advancingRef.current))
    }
  }

  return (
    <div className="p-4 md:p-6">
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1>Seller Dashboard</h1>
        <div className="flex flex-wrap gap-2">
          {activeTab === 'products' && (
            <Button variant="primary" onClick={openNewProductForm}>
              New product
            </Button>
          )}
          {/* /seller renders outside Layout (no Header), so it needs its own way out. */}
          <Button variant="secondary" onClick={signOut}>
            Sign out
          </Button>
        </div>
      </div>

      {metrics && (
        <div className="mb-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div className="rounded-md border border-neutral-200 px-4 py-3">
            <div className="text-xs uppercase tracking-wide text-neutral-600">Total revenue</div>
            <div className="text-xl font-semibold">{usd(metrics.totalRevenue)}</div>
          </div>
          <div className="rounded-md border border-neutral-200 px-4 py-3">
            <div className="text-xs uppercase tracking-wide text-neutral-600">Low stock products</div>
            <div className="text-xl font-semibold">{metrics.lowStockProducts.length}</div>
            {metrics.lowStockProducts.length > 0 && (
              <div className="mt-1 text-xs text-neutral-600">
                {metrics.lowStockProducts.map((p) => p.name).join(', ')}
              </div>
            )}
          </div>
        </div>
      )}

      {feedback && (
        <div className={`mb-4 ${feedback.type === 'success' ? 'callout-ok' : 'callout-alert'}`} role="status">
          <span>{feedback.message}</span>
          <button type="button" onClick={() => setFeedback(null)} aria-label="Dismiss" className="text-inherit">
            <X size={18} strokeWidth={1.5} />
          </button>
        </div>
      )}

      <div className="mb-4 flex gap-4 border-b border-neutral-200" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'products'}
          onClick={() => setActiveTab('products')}
          className={`px-1 pb-2 text-sm font-medium border-b-2 ${
            activeTab === 'products' ? 'border-accent-600 text-accent-700' : 'border-transparent text-neutral-600'
          }`}
        >
          Products
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'orders'}
          onClick={() => setActiveTab('orders')}
          className={`px-1 pb-2 text-sm font-medium border-b-2 ${
            activeTab === 'orders' ? 'border-accent-600 text-accent-700' : 'border-transparent text-neutral-600'
          }`}
        >
          Orders {orders.length > 0 && `(${orders.length})`}
        </button>
      </div>

      {isFormOpen && (
        <ProductForm
          initialProduct={editingProduct ?? undefined}
          categories={categories}
          onSubmit={handleSubmit}
          onCancel={closeForm}
          isSubmitting={isSubmitting}
        />
      )}

      {activeTab === 'products' && (
        <div className="overflow-x-auto">
          <Table className="min-w-[560px]">
            <TableHead>
              <TableRow>
                <TableHeader>Product</TableHeader>
                <TableHeader>Stock</TableHeader>
                <TableHeader>Price</TableHeader>
                <TableHeader>Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {products.map((product) => (
                <TableRow key={product.id}>
                  <TableCell>{product.name}</TableCell>
                  <TableCell className="readout">{product.stockQuantity}</TableCell>
                  <TableCell className="readout font-semibold">{usd(product.price)}</TableCell>
                  <TableCell>
                    <div className="flex gap-2">
                      <Button variant="secondary" onClick={() => openEditForm(product)}>
                        Edit
                      </Button>
                      <Button variant="secondary" onClick={() => handleDelete(product)}>
                        Delete
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {activeTab === 'orders' &&
        (ordersLoadError ? (
          <div className="callout-alert">
            <span>Could not load your orders.</span>
            <button type="button" onClick={fetchOrders} className="ml-2 underline">
              Retry
            </button>
          </div>
        ) : orders.length === 0 ? (
          <p className="text-sm text-neutral-600">No orders received yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <Table className="min-w-[840px]">
              <TableHead>
                <TableRow>
                  <TableHeader>Order</TableHeader>
                  <TableHeader>Date</TableHeader>
                  <TableHeader>Status</TableHeader>
                  <TableHeader>Items</TableHeader>
                  <TableHeader>Subtotal</TableHeader>
                  <TableHeader>Shipping</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {orders.map((order) => {
                  const shipment = shipmentBadge(order)
                  const action = nextFulfillmentAction(order)
                  return (
                    <TableRow key={order.orderId}>
                      <TableCell>#{order.orderId}</TableCell>
                      <TableCell>{new Date(order.createdAt).toLocaleDateString()}</TableCell>
                      <TableCell>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[order.status]}`}>
                          {order.status}
                        </span>
                      </TableCell>
                      <TableCell>
                        {order.items.map((item) => `${item.quantity}× #${item.productId}`).join(', ')}
                      </TableCell>
                      <TableCell>{usd(order.subtotal)}</TableCell>
                      <TableCell>
                        <div className="flex flex-wrap items-center gap-2">
                          {shipment ? (
                            <span className={shipment.className}>{shipment.label}</span>
                          ) : (
                            <span className="text-neutral-600">—</span>
                          )}
                          {action && (
                            <Button
                              variant="secondary"
                              onClick={() => handleAdvance(order)}
                              disabled={advancingOrderIds.has(order.orderId)}
                            >
                              {action.label}
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        ))}
    </div>
  )
}
