import { useEffect, useState } from 'react'
import { Product, sellersApi } from '../services/api'

const SELLER_ID = 1

export default function SellerDashboard() {
  const [products, setProducts] = useState<Product[]>([])

  useEffect(() => {
    sellersApi.getInventory(SELLER_ID).then((page) => setProducts(page.content)).catch(console.error)
  }, [])

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4">Painel do Vendedor</h1>
      <table className="w-full text-left border-collapse">
        <thead>
          <tr className="border-b">
            <th className="py-2">Produto</th>
            <th className="py-2">Estoque</th>
            <th className="py-2">Preço</th>
          </tr>
        </thead>
        <tbody>
          {products.map((product) => (
            <tr key={product.id} className="border-b">
              <td className="py-2">{product.name}</td>
              <td className="py-2">{product.stockQuantity}</td>
              <td className="py-2">R$ {product.price}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
