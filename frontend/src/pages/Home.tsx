import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { catalogApi, Product } from '../services/api'

export default function Home() {
  const [products, setProducts] = useState<Product[]>([])

  useEffect(() => {
    catalogApi.search().then((page) => setProducts(page.content)).catch(console.error)
  }, [])

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4">Catálogo</h1>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {products.map((product) => (
          <Link key={product.id} to={`/products/${product.id}`} className="border rounded-lg p-4 hover:shadow">
            <p className="font-semibold">{product.name}</p>
            <p className="text-sm text-gray-500">R$ {product.price}</p>
          </Link>
        ))}
      </div>
    </div>
  )
}
