import { act, fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

vi.mock('../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../services/api')>()
  return {
    ...actual,
    listsApi: {
      ...actual.listsApi,
      listMine: vi.fn(),
      create: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { listsApi, type WishListView } from '../services/api'
import { AUTH_STORAGE_KEY } from '../services/auth-token'
import { AuthProvider } from './AuthContext'
import { ListsProvider, useLists } from './ListsContext'

const mockedListsApi = vi.mocked(listsApi)

function listFor(id: number, name: string): WishListView {
  return { id, buyerId: 1, name, productIds: [], createdAt: '2026-09-01T12:00:00Z' }
}

function seedAuth() {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      id: 1,
      name: 'Test Buyer',
      email: 'buyer@example.com',
      role: 'BUYER',
      token: 'test-token',
      tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }),
  )
}

function Probe() {
  const { lists, status, reload, createList } = useLists()
  return (
    <div>
      <div data-testid="status">{status}</div>
      <div data-testid="names">{lists.map((l) => l.name).join(',')}</div>
      <button onClick={reload}>reload</button>
      <button onClick={() => createList('Fresh')}>create</button>
    </div>
  )
}

function renderProbe() {
  return render(
    <AuthProvider>
      <ListsProvider>
        <Probe />
      </ListsProvider>
    </AuthProvider>,
  )
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
})

describe('ListsProvider', () => {
  it('is idle without a signed-in user and never fetches', () => {
    renderProbe()

    expect(screen.getByTestId('status')).toHaveTextContent('idle')
    fireEvent.click(screen.getByText('reload'))
    expect(screen.getByTestId('status')).toHaveTextContent('idle')
    expect(mockedListsApi.listMine).not.toHaveBeenCalled()
  })

  it('goes loading → ready for a signed-in user', async () => {
    seedAuth()
    mockedListsApi.listMine.mockResolvedValue([listFor(1, 'Gifts')])

    renderProbe()

    expect(screen.getByTestId('status')).toHaveTextContent('loading')
    expect(await screen.findByText('ready')).toBeInTheDocument()
    expect(screen.getByTestId('names')).toHaveTextContent('Gifts')
  })

  it('reports errors and recovers on reload', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    seedAuth()
    mockedListsApi.listMine.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce([listFor(1, 'Gifts')])

    renderProbe()
    expect(await screen.findByText('error')).toBeInTheDocument()

    fireEvent.click(screen.getByText('reload'))
    expect(screen.getByTestId('status')).toHaveTextContent('loading')
    expect(await screen.findByText('ready')).toBeInTheDocument()
    expect(screen.getByTestId('names')).toHaveTextContent('Gifts')
    consoleError.mockRestore()
  })

  it('ignores an older response that settles after a newer reload', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    seedAuth()
    const first = deferred<WishListView[]>()
    const second = deferred<WishListView[]>()
    mockedListsApi.listMine.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    renderProbe()
    fireEvent.click(screen.getByText('reload'))

    await act(async () => second.resolve([listFor(2, 'Newer')]))
    expect(screen.getByTestId('status')).toHaveTextContent('ready')

    await act(async () => first.reject(new Error('late failure')))
    expect(screen.getByTestId('status')).toHaveTextContent('ready')
    expect(screen.getByTestId('names')).toHaveTextContent('Newer')
    consoleError.mockRestore()
  })

  it('prepends a created list to match the newest-first server order', async () => {
    seedAuth()
    mockedListsApi.listMine.mockResolvedValue([listFor(2, 'Second'), listFor(1, 'First')])
    mockedListsApi.create.mockResolvedValue(listFor(3, 'Fresh'))

    renderProbe()
    await screen.findByText('ready')

    fireEvent.click(screen.getByText('create'))
    expect(await screen.findByText('Fresh,Second,First')).toBeInTheDocument()
  })
})
