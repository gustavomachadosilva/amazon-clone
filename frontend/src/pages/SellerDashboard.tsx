import { useCallback, useEffect, useState } from 'react'
import { catalogApi, Product, ProductInput, sellersApi } from '../services/api'
import { Button, Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui'
import ProductForm from '../components/ProductForm'
import { useAuth } from '../context/AuthContext'

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
        setFeedback({ type: 'success', message: 'Produto atualizado com sucesso.' })
      } else {
        await catalogApi.create(input)
        setFeedback({ type: 'success', message: 'Produto criado com sucesso.' })
      }
      closeForm()
      fetchInventory()
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleDelete(product: Product) {
    if (!window.confirm(`Tem certeza que deseja excluir "${product.name}"?`)) return

    try {
      await catalogApi.remove(product.id)
      setFeedback({ type: 'success', message: 'Produto excluído com sucesso.' })
      fetchInventory()
    } catch {
      setFeedback({ type: 'error', message: 'Não foi possível excluir o produto. Tente novamente.' })
    }
  }

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-2xl font-bold">Painel do Vendedor</h1>
        <Button variant="primary" onClick={openNewProductForm}>
          Novo produto
        </Button>
      </div>

      {feedback && (
        <div
          className={`mb-4 rounded-md text-sm px-3 py-2 flex items-center justify-between ${
            feedback.type === 'success' ? 'bg-green-50 text-green-800' : 'bg-accent-50 text-accent-800'
          }`}
          role="status"
        >
          <span>{feedback.message}</span>
          <button
            type="button"
            onClick={() => setFeedback(null)}
            aria-label="Dispensar"
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

      <Table>
        <TableHead>
          <TableRow>
            <TableHeader>Produto</TableHeader>
            <TableHeader>Estoque</TableHeader>
            <TableHeader>Preço</TableHeader>
            <TableHeader>Ações</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {products.map((product) => (
            <TableRow key={product.id}>
              <TableCell>{product.name}</TableCell>
              <TableCell>{product.stockQuantity}</TableCell>
              <TableCell>R$ {product.price}</TableCell>
              <TableCell>
                <div className="flex gap-2">
                  <Button variant="secondary" onClick={() => openEditForm(product)}>
                    Editar
                  </Button>
                  <Button variant="secondary" onClick={() => handleDelete(product)}>
                    Excluir
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
