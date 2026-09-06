import { useEffect, useState } from 'react'
import { Product, sellersApi } from '../services/api'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '../components/ui'
import { useAuth } from '../context/AuthContext'

export default function SellerDashboard() {
  const { user } = useAuth()
  const [products, setProducts] = useState<Product[]>([])

  useEffect(() => {
    if (!user) return
    sellersApi.getInventory(user.id).then((page) => setProducts(page.content)).catch(console.error)
  }, [user])

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
