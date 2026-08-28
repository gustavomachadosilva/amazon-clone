import { useEffect, useState } from 'react'
import { Product, sellersApi } from '../services/api'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui'

const SELLER_ID = 1

export default function SellerDashboard() {
  const [products, setProducts] = useState<Product[]>([])

  useEffect(() => {
    sellersApi.getInventory(SELLER_ID).then((page) => setProducts(page.content)).catch(console.error)
  }, [])

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-4">Painel do Vendedor</h1>
      <Table>
        <TableHead>
          <TableRow>
            <TableHeader>Produto</TableHeader>
            <TableHeader>Estoque</TableHeader>
            <TableHeader>Preço</TableHeader>
          </TableRow>
        </TableHead>
        <TableBody>
          {products.map((product) => (
            <TableRow key={product.id}>
              <TableCell>{product.name}</TableCell>
              <TableCell>{product.stockQuantity}</TableCell>
              <TableCell>R$ {product.price}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
