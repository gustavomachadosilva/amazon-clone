import { PAYMENT_OPTIONS } from '../../lib/constants'
import type { PaymentMethod } from '../../services/api'

interface PaymentMethodOptionsProps {
  value: PaymentMethod
  onChange: (next: PaymentMethod) => void
  // Radio group name; must be unique on the page.
  name: string
  disabled?: boolean
}

// The payment method radios shared by checkout and the payment retry on the order details page.
// Only the options: the caller owns the heading or legend around them.
export default function PaymentMethodOptions({ value, onChange, name, disabled }: PaymentMethodOptionsProps) {
  return (
    <div className="flex flex-col gap-2">
      {(Object.keys(PAYMENT_OPTIONS) as PaymentMethod[]).map((key) => (
        <label key={key} className="radio flex">
          <input
            type="radio"
            name={name}
            checked={value === key}
            disabled={disabled}
            onChange={() => onChange(key)}
          />
          <span className="dot" />
          {PAYMENT_OPTIONS[key]}
        </label>
      ))}
    </div>
  )
}
