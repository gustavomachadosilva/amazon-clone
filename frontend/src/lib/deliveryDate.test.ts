import { describe, expect, it } from 'vitest'
import { addBusinessDays, formatDeliveryDate, getStandardDeliveryLabel } from './deliveryDate'

/** Local-date (not UTC) "YYYY-MM-DD" so assertions aren't timezone-sensitive. */
function isoLocal(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

describe('addBusinessDays', () => {
  it('skips the weekend when +1 business day from a Friday', () => {
    const friday = new Date(2026, 7, 14) // Friday, August 14, 2026
    const result = addBusinessDays(friday, 1)
    expect(result.getDay()).not.toBe(6) // not Saturday
    expect(isoLocal(result)).toBe('2026-08-17') // Monday
  })

  it('spans a weekend when +5 business days from a Wednesday', () => {
    const wednesday = new Date(2026, 7, 12) // Wednesday, August 12, 2026
    const result = addBusinessDays(wednesday, 5)
    expect(isoLocal(result)).toBe('2026-08-19') // following Wednesday
  })

  it('lands on Monday when +1 business day from a Saturday', () => {
    const saturday = new Date(2026, 7, 15) // Saturday, August 15, 2026
    const result = addBusinessDays(saturday, 1)
    expect(isoLocal(result)).toBe('2026-08-17') // Monday
  })
})

describe('formatDeliveryDate', () => {
  it('formats a known date as "Thursday, August 13"', () => {
    const date = new Date(2026, 7, 13) // Thursday, August 13, 2026
    expect(formatDeliveryDate(date)).toBe('Thursday, August 13')
  })
})

describe('getStandardDeliveryLabel', () => {
  it('is deterministic and matches addBusinessDays + formatDeliveryDate composition', () => {
    const from = new Date(2026, 7, 12) // Wednesday, August 12, 2026
    const expected = formatDeliveryDate(addBusinessDays(from, 5))
    expect(getStandardDeliveryLabel(from)).toBe(expected)
  })
})
