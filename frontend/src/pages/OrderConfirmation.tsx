import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { Blueprint, Button, Table, TableBody, TableCell, TableRow } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { ordersApi, type Order } from '../services/api'
import { DEFAULT_ADDRESS, PAYMENT_OPTIONS, SHIPPING_OPTIONS } from '../lib/constants'
import { usd } from '../lib/format'

interface LocationState {
  shippingLabel?: string
  paymentLabel?: string
}

export default function OrderConfirmation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const { user } = useAuth()
  const [order, setOrder] = useState<Order | null>(null)

  useEffect(() => {
    ordersApi.getById(Number(id)).then(setOrder)
  }, [id])

  if (!order) return <div className="mx-auto max-w-[820px] px-4 py-4 md:px-6 md:py-6">Loading…</div>

  const state = (location.state as LocationState) ?? {}
  const shippingLabel = state.shippingLabel ?? SHIPPING_OPTIONS.standard
  const paymentLabel = state.paymentLabel ?? PAYMENT_OPTIONS.card
  const address = `${DEFAULT_ADDRESS.street}, ${DEFAULT_ADDRESS.city} ${DEFAULT_ADDRESS.state} ${DEFAULT_ADDRESS.zip}`

  return (
    <div className="mx-auto max-w-[820px] px-4 py-4 md:px-6 md:py-6">
      <Blueprint className="p-5 md:p-[34px]">
        <div className="kick">Order {order.id}</div>
        <h1 className="text-[28px] md:text-[40px]">Order placed, thanks.</h1>
        <p className="text-[#5d5d60]">
          A confirmation was sent to {user?.email ?? 'you'}. Arriving Thursday, August 13.
        </p>

        <div className="overflow-x-auto">
          <Table>
            <TableBody>
              <TableRow>
                <TableCell>Order total</TableCell>
                <TableCell>{usd(order.totalAmount)}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Payment</TableCell>
                <TableCell>{paymentLabel}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Delivery</TableCell>
                <TableCell>{shippingLabel}</TableCell>
              </TableRow>
              <TableRow>
                <TableCell>Address</TableCell>
                <TableCell>{address}</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </div>

        <div className="mt-4 flex flex-col gap-3 sm:flex-row">
          <Button variant="primary" onClick={() => navigate('/orders')}>
            View your orders
          </Button>
          <Button variant="secondary" onClick={() => navigate('/')}>
            Back to the store
          </Button>
        </div>
      </Blueprint>
    </div>
  )
}
