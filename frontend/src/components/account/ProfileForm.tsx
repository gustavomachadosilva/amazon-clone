import { useEffect, useRef, useState } from 'react'
import { Button, Input } from '../ui'
import { useAuth } from '../../context/AuthContext'
import { ApiRequestError, usersApi, type UserProfile } from '../../services/api'

const MAX_LENGTH = 255

type Field = 'name' | 'email'
type FieldErrors = Partial<Record<Field, string>>

function validate(name: string, email: string): FieldErrors {
  const errors: FieldErrors = {}
  if (!name) errors.name = 'Enter your name.'
  else if (name.length > MAX_LENGTH) errors.name = `Must be ${MAX_LENGTH} characters or fewer.`
  if (!email.includes('@')) errors.email = 'Enter a valid email address.'
  else if (email.length > MAX_LENGTH) errors.email = `Must be ${MAX_LENGTH} characters or fewer.`
  return errors
}

interface ProfileFormProps {
  // The saved values: the form starts from them and "no changes" is measured against them.
  initial: { name: string; email: string }
  onSaved: (updated: UserProfile) => void
  onCancel: () => void
}

// Edits the signed-in user's name and email (PATCH /api/users/me). On success the AuthContext
// user is updated too, so the header greeting changes without signing in again.
export default function ProfileForm({ initial, onSaved, onCancel }: ProfileFormProps) {
  const { updateUser } = useAuth()
  const [values, setValues] = useState(initial)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  // Field errors render inline through Input; this is only for errors not tied to one field.
  const [formError, setFormError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const nameRef = useRef<HTMLInputElement>(null)

  // The form opens in place of the read-only view, so move focus into it.
  useEffect(() => {
    nameRef.current?.focus()
  }, [])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setInfo(null)
    setFormError(null)

    const name = values.name.trim()
    const email = values.email.trim()
    const errors = validate(name, email)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    if (name === initial.name && email === initial.email) {
      setInfo('No changes to save.')
      return
    }

    setIsSubmitting(true)
    try {
      const updated = await usersApi.updateMe({ name, email })
      updateUser({ name: updated.name, email: updated.email })
      onSaved(updated)
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 409) {
        setFieldErrors({ email: 'That email is already in use by another account.' })
      } else if (e instanceof ApiRequestError && e.status === 400) {
        setFormError('Please check the information provided.')
      } else if (e instanceof ApiRequestError && e.status === 401) {
        setFormError('Your session has expired. Please sign in again.')
      } else {
        setFormError('Could not save your changes. Please try again.')
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
        ref={nameRef}
        label="Name"
        autoComplete="name"
        value={values.name}
        error={fieldErrors.name}
        onChange={(e) => setValues({ ...values, name: e.target.value })}
      />
      <Input
        label="Email"
        type="email"
        autoComplete="email"
        value={values.email}
        error={fieldErrors.email}
        onChange={(e) => setValues({ ...values, email: e.target.value })}
      />

      {formError && (
        <div role="alert" className="callout-alert">
          {formError}
        </div>
      )}
      {info && (
        <p role="status" className="text-[16.5px] text-paper-700">
          {info}
        </p>
      )}

      <div className="flex flex-wrap gap-3">
        <Button variant="primary" type="submit" disabled={isSubmitting}>
          Save
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
