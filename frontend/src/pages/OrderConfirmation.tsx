import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Blueprint, Button, Table, TableBody, TableCell, TableRow } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { ordersApi, type Order } from '../services/api'
import { PAYMENT_OPTIONS, SHIPPING_OPTIONS } from '../lib/constants'
import { getStandardDeliveryLabel } from '../lib/deliveryDate'
import { usd } from '../lib/format'

export default function OrderConfirmation() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuth()
  const [order, setOrder] = useState<Order | null>(null)

  useEffect(() => {
    ordersApi.getById(Number(id)).then(setOrder)
  }, [id])

  if (!order) return <div className="mx-auto max-w-[900px] px-4 py-4 md:px-6 md:py-6">Loading…</div>

  const shippingLabel = order.shippingMethod ? SHIPPING_OPTIONS[order.shippingMethod] : 'Not available'
  const paymentLabel = order.paymentMethod ? PAYMENT_OPTIONS[order.paymentMethod] : 'Not available'
  const address = order.address
    ? `${order.address.street}, ${order.address.city} ${order.address.state} ${order.address.zip}`
    : 'Not available'

  return (
    <div className="mx-auto max-w-[900px] px-4 py-4 md:px-6 md:py-6">
      <Blueprint className="p-5 md:p-[34px]">
        <h1 className="text-[38px] md:text-[54px]">Order placed, thanks.</h1>
        <div className="mb-2 flex flex-wrap items-center gap-2.5">
          <span className="stamp">Manifest #{order.id}</span>
          <span className="tag tag-accent-2">Logged for dispatch</span>
        </div>
        <p className="text-paper-700">
          A confirmation was sent to {user?.email ?? 'you'}. Arriving {getStandardDeliveryLabel()}.
        </p>

        <div className="overflow-x-auto">
          <Table>
            <TableBody>
              <TableRow>
                <TableCell>Order total</TableCell>
                <TableCell className="readout font-semibold">{usd(order.totalAmount)}</TableCell>
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
