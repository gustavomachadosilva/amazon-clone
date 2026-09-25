import { vi } from 'vitest'
import { ApiRequestError, ordersApi, resolveApiUrl, usersApi } from './api'
import { onUnauthorized } from './auth-events'
import { AUTH_STORAGE_KEY } from './auth-token'

const fetchMock = vi.fn()
const listener = vi.fn()
let unsubscribe: () => void

function storeSession(token: string, expiresInMs = 60 * 60 * 1000) {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      id: 1,
      name: 'Test Buyer',
      email: 'buyer@example.com',
      role: 'BUYER',
      token,
      tokenExpiresAt: new Date(Date.now() + expiresInMs).toISOString(),
    }),
  )
}

function respondWith(status: number) {
  fetchMock.mockResolvedValue(new Response(null, { status }))
}

beforeEach(() => {
  localStorage.clear()
  fetchMock.mockReset()
  listener.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  unsubscribe = onUnauthorized(listener)
})

afterEach(() => {
  unsubscribe()
  vi.unstubAllGlobals()
})

describe('api request 401 handling', () => {
  it('notifies with the session token on a 401 and still rejects with ApiRequestError', async () => {
    storeSession('valid-token')
    respondWith(401)

    const error = await ordersApi.listByBuyer().catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiRequestError)
    expect((error as ApiRequestError).status).toBe(401)
    expect(listener).toHaveBeenCalledTimes(1)
    expect(listener).toHaveBeenCalledWith('valid-token')
  })

  it('notifies when the stored session had already expired (request sent without a token)', async () => {
    storeSession('expired-token', -1000)
    respondWith(401)

    await expect(ordersApi.listByBuyer()).rejects.toBeInstanceOf(ApiRequestError)

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.headers).not.toHaveProperty('Authorization')
    expect(listener).toHaveBeenCalledWith('expired-token')
  })

  it('does not notify on a 401 from login (wrong credentials)', async () => {
    storeSession('valid-token')
    respondWith(401)

    await expect(usersApi.login('buyer@example.com', 'wrong')).rejects.toBeInstanceOf(ApiRequestError)
    expect(listener).not.toHaveBeenCalled()
  })

  it('does not notify on a 401 when there is no stored session', async () => {
    respondWith(401)

    await expect(ordersApi.listByBuyer()).rejects.toBeInstanceOf(ApiRequestError)
    expect(listener).not.toHaveBeenCalled()
  })

  it.each([400, 403])('does not notify on a %i', async (status) => {
    storeSession('valid-token')
    respondWith(status)

    await expect(ordersApi.listByBuyer()).rejects.toBeInstanceOf(ApiRequestError)
    expect(listener).not.toHaveBeenCalled()
  })

  it('does not notify when the stored session changed while the request was in flight', async () => {
    storeSession('old-token')
    fetchMock.mockImplementation(async () => {
      storeSession('new-token')
      return new Response(null, { status: 401 })
    })

    await expect(ordersApi.listByBuyer()).rejects.toBeInstanceOf(ApiRequestError)
    expect(listener).not.toHaveBeenCalled()
  })
})

describe('resolveApiUrl', () => {
  it('prefixes relative backend paths with the API base', () => {
    expect(resolveApiUrl('/api/reviews/media/7')).toBe('http://localhost:8080/api/reviews/media/7')
    expect(resolveApiUrl('api/reviews/media/7')).toBe('http://localhost:8080/api/reviews/media/7')
  })

  it('leaves absolute and blob URLs unchanged', () => {
    expect(resolveApiUrl('https://cdn.example.com/a.jpg')).toBe('https://cdn.example.com/a.jpg')
    expect(resolveApiUrl('blob:http://localhost:5173/abc')).toBe('blob:http://localhost:5173/abc')
  })
})
