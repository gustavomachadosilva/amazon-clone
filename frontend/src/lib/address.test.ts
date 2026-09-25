import { describe, expect, it } from 'vitest'
import { ADDRESS_MAX_LENGTH, EMPTY_ADDRESS, normalizeAddress, validateAddress } from './address'

const VALID = { fullName: 'Test Buyer', street: '1 Main St', city: 'Seattle', state: 'WA', zip: '98104' }

describe('normalizeAddress', () => {
  it('trims every field', () => {
    expect(
      normalizeAddress({ fullName: '  Test Buyer ', street: ' 1 Main St', city: 'Seattle ', state: ' WA ', zip: '98104 ' }),
    ).toEqual(VALID)
  })
})

describe('validateAddress', () => {
  it('accepts a complete address', () => {
    expect(validateAddress(VALID)).toEqual({})
  })

  it('requires every field', () => {
    expect(validateAddress(EMPTY_ADDRESS)).toEqual({
      fullName: 'Enter a full name.',
      zip: 'Enter a ZIP code.',
      street: 'Enter a street address.',
      city: 'Enter a city.',
      state: 'Enter a state.',
    })
  })

  it('rejects values longer than the backend column', () => {
    const errors = validateAddress({ ...VALID, street: 'x'.repeat(ADDRESS_MAX_LENGTH + 1) })
    expect(errors).toEqual({ street: `Must be ${ADDRESS_MAX_LENGTH} characters or fewer.` })
    expect(validateAddress({ ...VALID, street: 'x'.repeat(ADDRESS_MAX_LENGTH) })).toEqual({})
  })
})
