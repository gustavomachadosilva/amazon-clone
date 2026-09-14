import { useCallback, useEffect, useState } from 'react'
import { catalogApi, Product, ProductInput, sellersApi } from '../services/api'
import { Button, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui'
import ProductForm from '../components/ProductForm'
import { useAuth } from '../context/AuthContext'
import { usd } from '../lib/format'

interface Feedback {
  type: 'success' | 'error'
  message: string
}

export default function SellerDashboard() {
  const { user } = useAuth()
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingProduct, setEditingProduct] = useState<Product | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [feedback, setFeedback] = useState<Feedback | null>(null)

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

  return (
    <div className="p-4 md:p-6">
      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <h1 className="text-2xl font-bold">Seller Dashboard</h1>
        <Button variant="primary" onClick={openNewProductForm}>
          New product
        </Button>
      </div>

      {feedback && (
        <div
          className={`mb-4 rounded-md text-sm px-3 py-2 flex items-center justify-between ${
            feedback.type === 'success' ? 'bg-accent2-100 text-accent2-800' : 'bg-accent-100 text-accent-800'
          }`}
          role="status"
        >
          <span>{feedback.message}</span>
          <button
            type="button"
            onClick={() => setFeedback(null)}
            aria-label="Dismiss"
            className="ml-4 text-inherit"
          >
            ×
          </button>
        </div>
      )}

      {isFormOpen && (
        <ProductForm
          initialProduct={editingProduct ?? undefined}
          categories={categories}
          onSubmit={handleSubmit}
          onCancel={closeForm}
          isSubmitting={isSubmitting}
        />
      )}

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
                <TableCell>{product.stockQuantity}</TableCell>
                <TableCell>{usd(product.price)}</TableCell>
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
    </div>
  )
}
