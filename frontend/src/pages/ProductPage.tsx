import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { catalogApi, Product } from '../services/api'

export default function ProductPage() {
  const { id } = useParams()
  const [product, setProduct] = useState<Product | null>(null)

  useEffect(() => {
    if (id) catalogApi.getById(Number(id)).then(setProduct).catch(console.error)
  }, [id])

  if (!product) return <div className="p-6">Carregando...</div>

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold">{product.name}</h1>
      <p className="mt-2 text-gray-600">{product.description}</p>
      <p className="mt-4 text-xl font-semibold">R$ {product.price}</p>
      <button className="mt-4 bg-black text-white px-4 py-2 rounded">Adicionar ao carrinho</button>
    </div>
  )
}
