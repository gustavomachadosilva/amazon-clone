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
  brand: string
  modelNumber: string
  listPrice: string
  warrantyMonths: string
}

interface FormErrors {
  name?: string
  description?: string
  price?: string
  stockQuantity?: string
  category?: string
  imageUrl?: string
  brand?: string
  modelNumber?: string
  listPrice?: string
  warrantyMonths?: string
}

function buildInitialState(product?: Product): FormState {
  return {
    name: product?.name ?? '',
    description: product?.description ?? '',
    price: product?.price !== undefined ? String(product.price) : '',
    stockQuantity: product?.stockQuantity !== undefined ? String(product.stockQuantity) : '',
    category: product?.category ?? '',
    imageUrl: product?.imageUrl ?? '',
    brand: product?.brand ?? '',
    modelNumber: product?.modelNumber ?? '',
    listPrice: product?.listPrice != null ? String(product.listPrice) : '',
    warrantyMonths: product?.warrantyMonths != null ? String(product.warrantyMonths) : '',
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

  // Repopulate the form when initialProduct changes (e.g. user clicks "Edit" on a
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
    const brand = form.brand.trim()
    const modelNumber = form.modelNumber.trim()

    if (!name) {
      nextErrors.name = 'Name is required.'
    } else if (name.length > 255) {
      nextErrors.name = 'Name must be at most 255 characters.'
    }

    if (description.length > 2000) {
      nextErrors.description = 'Description must be at most 2000 characters.'
    }

    const price = Number(form.price)
    if (form.price.trim() === '' || Number.isNaN(price)) {
      nextErrors.price = 'Price is required.'
    } else if (price <= 0) {
      nextErrors.price = 'Price must be greater than zero.'
    }

    const stockQuantity = Number(form.stockQuantity)
    if (form.stockQuantity.trim() === '' || Number.isNaN(stockQuantity)) {
      nextErrors.stockQuantity = 'Stock is required.'
    } else if (!Number.isInteger(stockQuantity) || stockQuantity < 0) {
      nextErrors.stockQuantity = 'Stock must be a whole number of zero or more.'
    }

    if (!category) {
      nextErrors.category = 'Category is required.'
    }

    if (imageUrl.length > 1000) {
      nextErrors.imageUrl = 'Image URL must be at most 1000 characters.'
    }

    if (brand.length > 255) {
      nextErrors.brand = 'Brand must be at most 255 characters.'
    }

    if (modelNumber.length > 255) {
      nextErrors.modelNumber = 'Model number must be at most 255 characters.'
    }

    let listPrice: number | undefined
    if (form.listPrice.trim() !== '') {
      listPrice = Number(form.listPrice)
      if (Number.isNaN(listPrice)) {
        nextErrors.listPrice = 'List price must be a number.'
      } else if (listPrice <= 0) {
        nextErrors.listPrice = 'List price must be greater than zero.'
      } else if (!Number.isNaN(price) && listPrice <= price) {
        nextErrors.listPrice = 'List price must be greater than the price.'
      }
    }

    let warrantyMonths: number | undefined
    if (form.warrantyMonths.trim() !== '') {
      warrantyMonths = Number(form.warrantyMonths)
      if (!Number.isInteger(warrantyMonths) || warrantyMonths < 0) {
        nextErrors.warrantyMonths = 'Warranty must be a whole number of months, zero or more.'
      }
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
        brand: brand || undefined,
        modelNumber: modelNumber || undefined,
        listPrice,
        warrantyMonths,
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
          setFormError('You do not have permission to edit this product.')
        } else if (e.status === 404) {
          setFormError('Product not found — it may have been removed.')
        } else {
          setFormError('Could not save the product. Please try again.')
        }
      } else {
        setFormError('Could not save the product. Please try again.')
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} className="card mb-6 p-6">
      <h2 className="mb-4 text-lg">{isEditMode ? 'Edit product' : 'New product'}</h2>

      {formError && (
        <div className="callout-alert mb-4" role="alert">
          {formError}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Input
          label="Name"
          value={form.name}
          onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          error={errors.name}
          maxLength={255}
        />

        <Select
          label="Category"
          value={form.category}
          onChange={(e) => setForm((f) => ({ ...f, category: e.target.value }))}
          error={errors.category}
        >
          <option value="">Select a category</option>
          {categories.map((category) => (
            <option key={category} value={category}>
              {category}
            </option>
          ))}
        </Select>

        <Input
          label="Price"
          type="number"
          step="0.01"
          min="0"
          value={form.price}
          onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
          error={errors.price}
        />

        <Input
          label="Stock"
          type="number"
          step="1"
          min="0"
          value={form.stockQuantity}
          onChange={(e) => setForm((f) => ({ ...f, stockQuantity: e.target.value }))}
          error={errors.stockQuantity}
        />

        <Input
          label="Brand (optional)"
          value={form.brand}
          onChange={(e) => setForm((f) => ({ ...f, brand: e.target.value }))}
          error={errors.brand}
          maxLength={255}
        />

        <Input
          label="Model number (optional)"
          value={form.modelNumber}
          onChange={(e) => setForm((f) => ({ ...f, modelNumber: e.target.value }))}
          error={errors.modelNumber}
          maxLength={255}
        />

        <Input
          label="List price (optional, must exceed price)"
          type="number"
          step="0.01"
          min="0"
          value={form.listPrice}
          onChange={(e) => setForm((f) => ({ ...f, listPrice: e.target.value }))}
          error={errors.listPrice}
        />

        <Input
          label="Warranty (months, optional)"
          type="number"
          step="1"
          min="0"
          value={form.warrantyMonths}
          onChange={(e) => setForm((f) => ({ ...f, warrantyMonths: e.target.value }))}
          error={errors.warrantyMonths}
        />

        <Input
          label="Image URL"
          value={form.imageUrl}
          onChange={(e) => setForm((f) => ({ ...f, imageUrl: e.target.value }))}
          error={errors.imageUrl}
          maxLength={1000}
          containerClassName="md:col-span-2"
        />

        <Textarea
          label="Description"
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
          {isEditMode ? 'Save changes' : 'Save product'}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
