import { useState, type FormEvent } from 'react'
import { Button, Input, Select, Textarea } from './ui'
import { ApiRequestError, type Product, type ProductInput } from '../services/api'

export interface ProductFormProps {
  initialProduct?: Product
  categories: string[]
  onSubmit: (input: ProductInput) => Promise<void>
  onCancel: () => void
  isSubmitting: boolean
}

interface FormState {
  name: string
  description: string
  price: string
  stockQuantity: string
  category: string
  imageUrl: string
}

interface FormErrors {
  name?: string
  description?: string
  price?: string
  stockQuantity?: string
  category?: string
  imageUrl?: string
}

function buildInitialState(product?: Product): FormState {
  return {
    name: product?.name ?? '',
    description: product?.description ?? '',
    price: product?.price !== undefined ? String(product.price) : '',
    stockQuantity: product?.stockQuantity !== undefined ? String(product.stockQuantity) : '',
    category: product?.category ?? '',
    imageUrl: product?.imageUrl ?? '',
  }
}

export default function ProductForm({
  initialProduct,
  categories,
  onSubmit,
  onCancel,
  isSubmitting,
}: ProductFormProps) {
  const isEditMode = !!initialProduct
  const [form, setForm] = useState<FormState>(() => buildInitialState(initialProduct))
  const [errors, setErrors] = useState<FormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  // Repopulate the form when initialProduct changes (e.g. user clicks "Editar" on a
  // different product while the form is already open). Adjusting state during render
  // (rather than in an effect) avoids an extra render pass — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes
  const [prevInitialProduct, setPrevInitialProduct] = useState(initialProduct)
  if (initialProduct !== prevInitialProduct) {
    setPrevInitialProduct(initialProduct)
    setForm(buildInitialState(initialProduct))
    setErrors({})
    setFormError(null)
  }

  function validate(): { errors: FormErrors; input: ProductInput | null } {
    const nextErrors: FormErrors = {}
    const name = form.name.trim()
    const description = form.description.trim()
    const category = form.category.trim()
    const imageUrl = form.imageUrl.trim()

    if (!name) {
      nextErrors.name = 'Nome é obrigatório.'
    } else if (name.length > 255) {
      nextErrors.name = 'Nome deve ter no máximo 255 caracteres.'
    }

    if (description.length > 2000) {
      nextErrors.description = 'Descrição deve ter no máximo 2000 caracteres.'
    }

    const price = Number(form.price)
    if (form.price.trim() === '' || Number.isNaN(price)) {
      nextErrors.price = 'Preço é obrigatório.'
    } else if (price <= 0) {
      nextErrors.price = 'Preço deve ser maior que zero.'
    }

    const stockQuantity = Number(form.stockQuantity)
    if (form.stockQuantity.trim() === '' || Number.isNaN(stockQuantity)) {
      nextErrors.stockQuantity = 'Estoque é obrigatório.'
    } else if (!Number.isInteger(stockQuantity) || stockQuantity < 0) {
      nextErrors.stockQuantity = 'Estoque deve ser um número inteiro maior ou igual a zero.'
    }

    if (!category) {
      nextErrors.category = 'Categoria é obrigatória.'
    }

    if (imageUrl.length > 1000) {
      nextErrors.imageUrl = 'URL da imagem deve ter no máximo 1000 caracteres.'
    }

    if (Object.keys(nextErrors).length > 0) {
      return { errors: nextErrors, input: null }
    }

    return {
      errors: {},
      input: {
        name,
        description: description || undefined,
        price,
        stockQuantity,
        category,
        imageUrl: imageUrl || undefined,
      },
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)

    const { errors: validationErrors, input } = validate()
    setErrors(validationErrors)
    if (!input) return

    try {
      await onSubmit(input)
    } catch (e) {
      if (e instanceof ApiRequestError) {
        if (e.apiMessage) {
          setFormError(e.apiMessage)
        } else if (e.status === 403) {
          setFormError('Você não tem permissão para editar este produto.')
        } else if (e.status === 404) {
          setFormError('Produto não encontrado — pode ter sido removido.')
        } else {
          setFormError('Não foi possível salvar o produto. Tente novamente.')
        }
      } else {
        setFormError('Não foi possível salvar o produto. Tente novamente.')
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} className="border border-neutral-200 rounded-lg p-6 mb-6 bg-white">
      <h2 className="text-lg font-semibold mb-4">
        {isEditMode ? 'Editar produto' : 'Novo produto'}
      </h2>

      {formError && (
        <div className="mb-4 rounded-md bg-accent-50 text-accent-800 text-sm px-3 py-2" role="alert">
          {formError}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Input
          label="Nome"
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          error={errors.name}
          maxLength={255}
        />

        <Select
          label="Categoria"
          value={form.category}
          onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
          error={errors.category}
        >
          <option value="">Selecione uma categoria</option>
          {categories.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </Select>

        <Input
          label="Preço"
          type="number"
          step="0.01"
          min="0"
          value={form.price}
          onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
          error={errors.price}
        />

        <Input
          label="Estoque"
          type="number"
          step="1"
          min="0"
          value={form.stockQuantity}
          onChange={(e) => setForm((f) => ({ ...f, stockQuantity: e.target.value }))}
          error={errors.stockQuantity}
        />

        <Input
          label="URL da imagem"
          value={form.imageUrl}
          onChange={(e) => setForm((f) => ({ ...f, imageUrl: e.target.value }))}
          error={errors.imageUrl}
          maxLength={1000}
          containerClassName="md:col-span-2"
        />

        <Textarea
          label="Descrição"
          value={form.description}
          onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
          error={errors.description}
          maxLength={2000}
          containerClassName="md:col-span-2"
          rows={4}
        />
      </div>

      <div className="flex gap-3 mt-6">
        <Button type="submit" variant="primary" disabled={isSubmitting}>
          {isEditMode ? 'Salvar alterações' : 'Salvar produto'}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          Cancelar
        </Button>
      </div>
    </form>
  )
}
