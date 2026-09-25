import { Input } from '../ui'
import { ADDRESS_MAX_LENGTH, type AddressErrors, type AddressField } from '../../lib/address'
import type { OrderAddress } from '../../services/api'

interface AddressFieldsProps {
  value: OrderAddress
  onChange: (next: OrderAddress) => void
  errors?: AddressErrors
  disabled?: boolean
  // Two columns fit the checkout page; one suits narrow places like the order summary aside.
  columns?: 1 | 2
}

// The shipping address inputs shared by checkout and the order details page. Only the fields:
// the caller owns the form, validation and submit buttons.
export default function AddressFields({ value, onChange, errors, disabled, columns = 2 }: AddressFieldsProps) {
  const twoColumns = columns === 2

  function update(field: AddressField, next: string) {
    onChange({ ...value, [field]: next })
  }

  return (
    <div className={twoColumns ? 'grid grid-cols-1 gap-3 sm:grid-cols-2' : 'grid grid-cols-1 gap-3'}>
      <Input
        label="Full name"
        autoComplete="name"
        maxLength={ADDRESS_MAX_LENGTH}
        disabled={disabled}
        value={value.fullName}
        error={errors?.fullName}
        onChange={(e) => update('fullName', e.target.value)}
      />
      <Input
        label="ZIP code"
        autoComplete="postal-code"
        maxLength={ADDRESS_MAX_LENGTH}
        disabled={disabled}
        value={value.zip}
        error={errors?.zip}
        onChange={(e) => update('zip', e.target.value)}
      />
      <Input
        label="Street address"
        autoComplete="street-address"
        containerClassName={twoColumns ? 'sm:col-span-2' : undefined}
        maxLength={ADDRESS_MAX_LENGTH}
        disabled={disabled}
        value={value.street}
        error={errors?.street}
        onChange={(e) => update('street', e.target.value)}
      />
      <Input
        label="City"
        autoComplete="address-level2"
        maxLength={ADDRESS_MAX_LENGTH}
        disabled={disabled}
        value={value.city}
        error={errors?.city}
        onChange={(e) => update('city', e.target.value)}
      />
      <Input
        label="State"
        autoComplete="address-level1"
        maxLength={ADDRESS_MAX_LENGTH}
        disabled={disabled}
        value={value.state}
        error={errors?.state}
        onChange={(e) => update('state', e.target.value)}
      />
    </div>
  )
}
