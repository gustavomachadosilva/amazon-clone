import { Route, Routes, Link } from 'react-router-dom'
import Home from './pages/Home'
import ProductPage from './pages/ProductPage'
import Cart from './pages/Cart'
import SellerDashboard from './pages/SellerDashboard'

export default function App() {
  return (
    <div>
      <nav className="flex gap-4 p-4 border-b">
        <Link to="/">Mercatto</Link>
        <Link to="/cart">Carrinho</Link>
        <Link to="/seller">Painel do Vendedor</Link>
      </nav>

      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/products/:id" element={<ProductPage />} />
        <Route path="/cart" element={<Cart />} />
        <Route path="/seller" element={<SellerDashboard />} />
      </Routes>
    </div>
  )
}
