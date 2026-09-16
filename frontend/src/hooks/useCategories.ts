import { useEffect, useState } from 'react'
import { catalogApi } from '../services/api'

export function useCategories() {
  const [categories, setCategories] = useState<string[]>(['All'])

  useEffect(() => {
    catalogApi
      .getCategories()
      .then((list) => setCategories(['All', ...list]))
      .catch(() => {})
  }, [])

  return categories
}
