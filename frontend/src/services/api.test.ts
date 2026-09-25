import { vi } from 'vitest'
import { ApiRequestError, ordersApi, resolveApiUrl, reviewsApi, usersApi } from './api'
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

describe('reviewsApi.create', () => {
  const payload = { stars: 5, title: 'Great', text: 'Works well' }

  function respondWithReview() {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ id: 1, media: [] }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
  }

  it('sends JSON with a Content-Type header when there are no files', async () => {
    storeSession('valid-token')
    respondWithReview()

    await reviewsApi.create(7, payload)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toMatch(/\/api\/reviews\/products\/7$/)
    expect(init.method).toBe('POST')
    expect(init.headers).toMatchObject({
      'Content-Type': 'application/json',
      Authorization: 'Bearer valid-token',
    })
    expect(init.body).toBe(JSON.stringify(payload))
  })

  it('sends multipart FormData without a fixed Content-Type when there are files', async () => {
    storeSession('valid-token')
    respondWithReview()
    const photo = new File(['img'], 'photo.png', { type: 'image/png' })
    const clip = new File(['vid'], 'clip.mp4', { type: 'video/mp4' })

    await reviewsApi.create(7, payload, [photo, clip])

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toMatch(/\/api\/reviews\/products\/7$/)
    expect(init.method).toBe('POST')
    expect(init.headers).not.toHaveProperty('Content-Type')
    expect(init.headers).toMatchObject({ Authorization: 'Bearer valid-token' })
    expect(init.body).toBeInstanceOf(FormData)

    const form = init.body as FormData
    const review = form.get('review') as Blob
    expect(review).toBeInstanceOf(Blob)
    expect(review.type).toBe('application/json')
    // jsdom's Blob has no .text(); FileReader reads it instead.
    const json = await new Promise<string>((resolve) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result as string)
      reader.readAsText(review)
    })
    expect(JSON.parse(json)).toEqual(payload)
    expect((form.getAll('files') as File[]).map((f) => f.name)).toEqual(['photo.png', 'clip.mp4'])
  })

  it('keeps the JSON Content-Type for other callers', async () => {
    storeSession('valid-token')
    respondWithReview()

    await usersApi.updateMe({ name: 'New' })

    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json' })
  })
})
