import type { OrderAddress } from '../services/api'

// Matches the backend's varchar(255) columns; longer values would fail on insert.
export const ADDRESS_MAX_LENGTH = 255

export type AddressField = keyof OrderAddress
export type AddressErrors = Partial<Record<AddressField, string>>

export const EMPTY_ADDRESS: OrderAddress = { fullName: '', street: '', city: '', state: '', zip: '' }

const REQUIRED_MESSAGES: Record<AddressField, string> = {
  fullName: 'Enter a full name.',
  zip: 'Enter a ZIP code.',
  street: 'Enter a street address.',
  city: 'Enter a city.',
  state: 'Enter a state.',
}

export function normalizeAddress(address: OrderAddress): OrderAddress {
  return {
    fullName: address.fullName.trim(),
    street: address.street.trim(),
    city: address.city.trim(),
    state: address.state.trim(),
    zip: address.zip.trim(),
  }
}

/** Expects a normalized address; returns one message per invalid field (empty when valid). */
export function validateAddress(address: OrderAddress): AddressErrors {
  const errors: AddressErrors = {}
  for (const field of Object.keys(REQUIRED_MESSAGES) as AddressField[]) {
    const value = address[field]
    if (!value) errors[field] = REQUIRED_MESSAGES[field]
    else if (value.length > ADDRESS_MAX_LENGTH) errors[field] = `Must be ${ADDRESS_MAX_LENGTH} characters or fewer.`
  }
  return errors
}
