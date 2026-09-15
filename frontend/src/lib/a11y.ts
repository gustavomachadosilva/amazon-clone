import type { KeyboardEvent } from 'react'

export function onEnterKey(handler: () => void) {
  return (event: KeyboardEvent) => {
    if (event.key !== 'Enter') return
    event.preventDefault()
    handler()
  }
}

export function onEnterOrSpaceKey(handler: () => void) {
  return (event: KeyboardEvent) => {
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault()
    handler()
  }
}
