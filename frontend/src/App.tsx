import { Route, Routes } from 'react-router-dom'
import RequireRole from './components/auth/RequireRole'
import Layout from './components/layout/Layout'
import Home from './pages/Home'
import Search from './pages/Search'
import Product from './pages/Product'
import WriteReview from './pages/WriteReview'
import Lists from './pages/Lists'
import Cart from './pages/Cart'
import SignIn from './pages/SignIn'
import Checkout from './pages/Checkout'
import OrderConfirmation from './pages/OrderConfirmation'
import Orders from './pages/Orders'
import SellerDashboard from './pages/SellerDashboard'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/search" element={<Search />} />
        <Route path="/product/:id" element={<Product />} />
        <Route path="/product/:id/review" element={<WriteReview />} />
        <Route path="/lists" element={<Lists />} />
        <Route path="/cart" element={<Cart />} />
        <Route path="/signin" element={<SignIn />} />
        <Route path="/checkout" element={<Checkout />} />
        <Route path="/order/:id" element={<OrderConfirmation />} />
        <Route path="/orders" element={<Orders />} />
      </Route>
      <Route
        path="/seller"
        element={
          <RequireRole role="SELLER">
            <SellerDashboard />
          </RequireRole>
        }
      />
    </Routes>
  )
}
