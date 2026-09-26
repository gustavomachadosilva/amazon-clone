import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { renderWithProviders } from '../../test/test-utils'

vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>()
  return {
    ...actual,
    usersApi: {
      ...actual.usersApi,
      updateMe: vi.fn(),
    },
    // CartProvider fetches the cart whenever someone is signed in; keep that off the network.
    cartApi: {
      ...actual.cartApi,
      get: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { ApiRequestError, cartApi, usersApi, type UserProfile } from '../../services/api'
import { AUTH_STORAGE_KEY } from '../../services/auth-token'
import ProfileForm from './ProfileForm'

const mockedUsersApi = vi.mocked(usersApi)
const mockedCartApi = vi.mocked(cartApi)

const INITIAL = { name: 'Test Buyer', email: 'buyer@example.com' }

const UPDATED: UserProfile = {
  id: 1,
  name: 'New Name',
  email: 'new@example.com',
  role: 'BUYER',
  createdAt: '2026-03-15T12:00:00Z',
}

function seedAuth() {
  localStorage.setItem(
    AUTH_STORAGE_KEY,
    JSON.stringify({
      id: 1,
      ...INITIAL,
      role: 'BUYER',
      token: 'test-token',
      tokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    }),
  )
}

function storedUser() {
  return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) ?? 'null')
}

async function renderForm() {
  const onSaved = vi.fn()
  const onCancel = vi.fn()
  renderWithProviders(<ProfileForm initial={INITIAL} onSaved={onSaved} onCancel={onCancel} />)
  // Let CartProvider's cart load settle inside act().
  await act(async () => {})
  return { onSaved, onCancel }
}

function fill(name: string, email: string) {
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: name } })
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
  fireEvent.click(screen.getByRole('button', { name: 'Save' }))
}

beforeEach(() => {
  localStorage.clear()
  vi.clearAllMocks()
  seedAuth()
  mockedCartApi.get.mockResolvedValue({ userId: 1, items: [], savedForLater: [], itemCount: 0, total: 0 })
})

describe('ProfileForm', () => {
  it('starts from the saved values with the Name field focused', async () => {
    await renderForm()

    expect(screen.getByLabelText('Name')).toHaveValue('Test Buyer')
    expect(screen.getByLabelText('Email')).toHaveValue('buyer@example.com')
    expect(screen.getByLabelText('Name')).toHaveFocus()
  })

  it('saves trimmed values, updates the session and reports the result', async () => {
    mockedUsersApi.updateMe.mockResolvedValue(UPDATED)
    const { onSaved } = await renderForm()

    fill('  New Name  ', ' new@example.com ')

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(UPDATED))
    expect(mockedUsersApi.updateMe).toHaveBeenCalledWith({ name: 'New Name', email: 'new@example.com' })
    expect(storedUser()).toMatchObject({ name: 'New Name', email: 'new@example.com', token: 'test-token' })
  })

  it('shows a clear message when the email belongs to another account', async () => {
    mockedUsersApi.updateMe.mockRejectedValue(new ApiRequestError(409, 'Email already registered'))
    const { onSaved } = await renderForm()

    fill('New Name', 'taken@example.com')

    expect(await screen.findByText('That email is already in use by another account.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(onSaved).not.toHaveBeenCalled()
    expect(storedUser()).toMatchObject(INITIAL)
  })

  it('shows a generic error when saving fails unexpectedly', async () => {
    mockedUsersApi.updateMe.mockRejectedValue(new Error('network down'))
    const { onSaved } = await renderForm()

    fill('New Name', 'buyer@example.com')

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not save your changes. Please try again.')
    expect(onSaved).not.toHaveBeenCalled()
  })

  it('validates the fields before calling the API', async () => {
    await renderForm()

    fill('   ', 'not-an-email')

    expect(screen.getByText('Enter your name.')).toBeInTheDocument()
    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
    expect(mockedUsersApi.updateMe).not.toHaveBeenCalled()
  })

  it('rejects values longer than 255 characters', async () => {
    await renderForm()

    fill('a'.repeat(256), 'buyer@example.com')

    expect(screen.getByText('Must be 255 characters or fewer.')).toBeInTheDocument()
    expect(mockedUsersApi.updateMe).not.toHaveBeenCalled()
  })

  it('does not call the API when nothing changed', async () => {
    const { onSaved } = await renderForm()

    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(screen.getByRole('status')).toHaveTextContent('No changes to save.')
    expect(mockedUsersApi.updateMe).not.toHaveBeenCalled()
    expect(onSaved).not.toHaveBeenCalled()
  })

  it('cancels with the Cancel button or Escape without saving', async () => {
    const { onCancel } = await renderForm()

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Draft' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    fireEvent.keyDown(screen.getByLabelText('Name'), { key: 'Escape' })

    expect(onCancel).toHaveBeenCalledTimes(2)
    expect(mockedUsersApi.updateMe).not.toHaveBeenCalled()
  })
})
