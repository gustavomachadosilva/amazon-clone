import { useEffect, useState } from 'react'
import { catalogApi, type Product } from '../services/api'

export function useProductsByIds(ids: number[]) {
  const [products, setProducts] = useState<Map<number, Product>>(new Map())
  const [loading, setLoading] = useState(false)
  const key = ids.join(',')

  useEffect(() => {
    if (!ids.length) {
      setProducts(new Map())
      return
    }
    setLoading(true)
    Promise.all(ids.map((id) => catalogApi.getById(id).catch(() => null)))
      .then((list) =>
        setProducts(new Map(list.filter((p): p is Product => p !== null).map((p) => [p.id, p]))),
      )
      .finally(() => setLoading(false))
  }, [key]) // eslint-disable-line react-hooks/exhaustive-deps -- ids intentionally compared by joined key

  return { products, loading }
}
