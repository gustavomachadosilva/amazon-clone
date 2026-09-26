import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'

vi.mock('../../services/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/api')>()
  return {
    ...actual,
    usersApi: {
      ...actual.usersApi,
      changePassword: vi.fn(),
    },
  }
})

// Imported after the mock so they pick up the mocked module.
import { ApiRequestError, usersApi } from '../../services/api'
import PasswordForm from './PasswordForm'

const mockedUsersApi = vi.mocked(usersApi)

// PasswordForm doesn't touch the session, so it needs no providers.
function renderForm() {
  const onSaved = vi.fn()
  const onCancel = vi.fn()
  render(<PasswordForm onSaved={onSaved} onCancel={onCancel} />)
  return { onSaved, onCancel }
}

function fill(current: string, next: string, confirm = next) {
  fireEvent.change(screen.getByLabelText('Current password'), { target: { value: current } })
  fireEvent.change(screen.getByLabelText('New password'), { target: { value: next } })
  fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: confirm } })
  fireEvent.click(screen.getByRole('button', { name: 'Save password' }))
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('PasswordForm', () => {
  it('focuses the current password field when it opens', () => {
    renderForm()

    expect(screen.getByLabelText('Current password')).toHaveFocus()
  })

  it('changes the password and reports the result', async () => {
    mockedUsersApi.changePassword.mockResolvedValue(undefined)
    const { onSaved } = renderForm()

    fill('oldpassword', 'newpassword123')

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
    expect(mockedUsersApi.changePassword).toHaveBeenCalledWith({
      currentPassword: 'oldpassword',
      newPassword: 'newpassword123',
    })
  })

  it('says so when the current password is wrong', async () => {
    mockedUsersApi.changePassword.mockRejectedValue(new ApiRequestError(400, 'Senha atual incorreta'))
    const { onSaved } = renderForm()

    fill('wrongpassword', 'newpassword123')

    expect(await screen.findByText('Your current password is incorrect.')).toBeInTheDocument()
    expect(screen.getByLabelText('Current password')).toHaveAttribute('aria-invalid', 'true')
    expect(onSaved).not.toHaveBeenCalled()
  })

  it('tells the user to sign in again when the session has expired', async () => {
    mockedUsersApi.changePassword.mockRejectedValue(new ApiRequestError(401))
    const { onSaved } = renderForm()

    fill('oldpassword', 'newpassword123')

    expect(await screen.findByRole('alert')).toHaveTextContent('Your session has expired. Please sign in again.')
    expect(onSaved).not.toHaveBeenCalled()
  })

  it('requires the current password and at least 8 characters for the new one', () => {
    renderForm()

    fill('', 'short')

    expect(screen.getByText('Enter your current password.')).toBeInTheDocument()
    expect(screen.getByText('Password must be at least 8 characters.')).toBeInTheDocument()
    expect(mockedUsersApi.changePassword).not.toHaveBeenCalled()
  })

  it('rejects a confirmation that does not match', () => {
    renderForm()

    fill('oldpassword', 'newpassword123', 'newpassword124')

    expect(screen.getByText('Passwords do not match.')).toBeInTheDocument()
    expect(mockedUsersApi.changePassword).not.toHaveBeenCalled()
  })

  it('rejects a new password equal to the current one', () => {
    renderForm()

    fill('samepassword', 'samepassword')

    expect(screen.getByText('Your new password must be different from the current one.')).toBeInTheDocument()
    expect(mockedUsersApi.changePassword).not.toHaveBeenCalled()
  })

  it('measures the 72-character limit in bytes, like bcrypt', () => {
    renderForm()

    // 40 characters but 80 bytes in UTF-8: must be caught here, not reported as a wrong
    // current password after the backend rejects it.
    fill('oldpassword', 'é'.repeat(40))

    expect(screen.getByText(/Password is too long/)).toBeInTheDocument()
    expect(mockedUsersApi.changePassword).not.toHaveBeenCalled()
  })

  it('accepts a multi-byte password that fits in 72 bytes', async () => {
    mockedUsersApi.changePassword.mockResolvedValue(undefined)
    const { onSaved } = renderForm()

    fill('oldpassword', 'é'.repeat(36))

    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1))
  })

  it('cancels with the Cancel button or Escape without saving', () => {
    const { onCancel } = renderForm()

    fireEvent.change(screen.getByLabelText('Current password'), { target: { value: 'draft' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    fireEvent.keyDown(screen.getByLabelText('Current password'), { key: 'Escape' })

    expect(onCancel).toHaveBeenCalledTimes(2)
    expect(mockedUsersApi.changePassword).not.toHaveBeenCalled()
  })
})
