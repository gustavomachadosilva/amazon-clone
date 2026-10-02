import { useEffect, useRef, useState } from 'react'
import { Button, Input } from '../ui'
import { ApiRequestError, usersApi } from '../../services/api'

// Mirrors the backend rule for new passwords. bcrypt only uses the first 72 *bytes*, so the
// upper bound is measured in UTF-8 bytes: accented letters and emoji take 2-4 bytes each.
const MIN_LENGTH = 8
const MAX_BYTES = 72
const utf8 = new TextEncoder()

type Field = 'current' | 'next' | 'confirm'
type FieldErrors = Partial<Record<Field, string>>

const EMPTY = { current: '', next: '', confirm: '' }

function validate({ current, next, confirm }: typeof EMPTY): FieldErrors {
  const errors: FieldErrors = {}
  if (!current) errors.current = 'Enter your current password.'
  if (next.length < MIN_LENGTH) errors.next = `Password must be at least ${MIN_LENGTH} characters.`
  else if (utf8.encode(next).length > MAX_BYTES)
    errors.next = 'Password is too long. Use fewer characters (accented letters and emoji count as more than one).'
  else if (current && next === current) errors.next = 'Your new password must be different from the current one.'
  if (confirm !== next) errors.confirm = 'Passwords do not match.'
  return errors
}

interface PasswordFormProps {
  onSaved: () => void
  onCancel: () => void
}

// Changes the signed-in user's password (PUT /api/users/me/password). The session token stays
// valid afterwards, so there's no need to sign in again.
export default function PasswordForm({ onSaved, onCancel }: PasswordFormProps) {
  const [values, setValues] = useState(EMPTY)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  // Field errors render inline through Input; this is only for errors not tied to one field.
  const [formError, setFormError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const currentRef = useRef<HTMLInputElement>(null)

  // The form opens in place of the read-only view, so move focus into it.
  useEffect(() => {
    currentRef.current?.focus()
  }, [])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setFormError(null)

    const errors = validate(values)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setIsSubmitting(true)
    try {
      await usersApi.changePassword({ currentPassword: values.current, newPassword: values.next })
      onSaved()
    } catch (e) {
      // Client-side validation already rules out the other 400 causes (length in bytes, same
      // password), so a 400 here means the current password didn't match.
      if (e instanceof ApiRequestError && e.status === 400) {
        setFieldErrors({ current: 'Your current password is incorrect.' })
      } else if (e instanceof ApiRequestError && e.status === 401) {
        setFormError('Your session has expired. Please sign in again.')
      } else {
        setFormError('Could not change your password. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <form
      onSubmit={submit}
      onKeyDown={(e) => {
        if (e.key === 'Escape' && !isSubmitting) onCancel()
      }}
      noValidate
      className="flex flex-col gap-3"
    >
      <Input
        ref={currentRef}
        label="Current password"
        type="password"
        autoComplete="current-password"
        value={values.current}
        error={fieldErrors.current}
        onChange={(e) => setValues({ ...values, current: e.target.value })}
      />
      <Input
        label="New password"
        type="password"
        autoComplete="new-password"
        helperText={`At least ${MIN_LENGTH} characters.`}
        value={values.next}
        error={fieldErrors.next}
        onChange={(e) => setValues({ ...values, next: e.target.value })}
      />
      <Input
        label="Confirm new password"
        type="password"
        autoComplete="new-password"
        value={values.confirm}
        error={fieldErrors.confirm}
        onChange={(e) => setValues({ ...values, confirm: e.target.value })}
      />

      {formError && (
        <div role="alert" className="callout-alert">
          {formError}
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        <Button variant="primary" type="submit" disabled={isSubmitting}>
          Save password
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
