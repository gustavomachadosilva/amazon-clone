/** Adds N business days (skips Sat/Sun, no holiday calendar) to a date. */
export function addBusinessDays(from: Date, days: number): Date {
  const result = new Date(from)
  let added = 0
  while (added < days) {
    result.setDate(result.getDate() + 1)
    const day = result.getDay() // 0 = Sun, 6 = Sat
    if (day !== 0 && day !== 6) added++
  }
  return result
}

/** Formats a Date as "Thursday, August 13" — matches the existing copy style. */
export function formatDeliveryDate(date: Date): string {
  return date.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' })
}

const STANDARD_MAX_BUSINESS_DAYS = 5

export function getStandardDeliveryLabel(from: Date = new Date()): string {
  return formatDeliveryDate(addBusinessDays(from, STANDARD_MAX_BUSINESS_DAYS))
}
