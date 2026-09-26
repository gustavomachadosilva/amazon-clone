import { useEffect, useRef, useState } from 'react'
import { Blueprint, Button } from '../ui'
import type { UserProfile } from '../../services/api'
import PasswordForm from './PasswordForm'
import ProfileForm from './ProfileForm'

type Block = 'profile' | 'password'

interface AccountInfoSectionProps {
  name: string
  email: string
  onProfileSaved: (updated: UserProfile) => void
}

const BLOCK_CLASS = 'flex flex-col gap-3 border-t border-divider pt-4'

// "Your account information" on /account: name/email and password shown read-only, each with a
// button that swaps the block for its form in place. Only one block edits at a time — opening
// the other one discards the unsaved edits of the first.
export default function AccountInfoSection({ name, email, onProfileSaved }: AccountInfoSectionProps) {
  const [editing, setEditing] = useState<Block | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const editRef = useRef<HTMLButtonElement>(null)
  const passwordRef = useRef<HTMLButtonElement>(null)
  // Which trigger gets focus back once its form closes (the forms focus themselves on open).
  const returnFocus = useRef<Block | null>(null)

  useEffect(() => {
    if (editing !== null || returnFocus.current === null) return
    const target = returnFocus.current === 'profile' ? editRef : passwordRef
    returnFocus.current = null
    target.current?.focus()
  }, [editing])

  function open(block: Block) {
    setMessage(null)
    setEditing(block)
  }

  function close(block: Block, doneMessage?: string) {
    returnFocus.current = block
    setEditing(null)
    setMessage(doneMessage ?? null)
  }

  return (
    <Blueprint
      as="section"
      id="account-info"
      aria-labelledby="account-info-heading"
      className="flex scroll-mt-4 flex-col gap-4 bg-card p-4 md:p-6"
    >
      <h2 id="account-info-heading" className="card-title mb-0">
        Your account information
      </h2>

      {/* Always mounted so screen readers announce the message when it appears. */}
      <p role="status" className={message ? 'callout-ok' : 'sr-only'}>
        {message}
      </p>

      <div className={BLOCK_CLASS}>
        <h3 className="card-title mb-0 text-xl">Name and email</h3>
        {editing === 'profile' ? (
          <ProfileForm
            initial={{ name, email }}
            onSaved={(updated) => {
              onProfileSaved(updated)
              close('profile', 'Your name and email have been updated.')
            }}
            onCancel={() => close('profile')}
          />
        ) : (
          <div className="flex flex-wrap items-start justify-between gap-3">
            <dl className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1 text-[16px]">
              <dt className="text-paper-700">Name</dt>
              <dd className="break-words">{name}</dd>
              <dt className="text-paper-700">Email</dt>
              <dd className="break-all font-mono">{email}</dd>
            </dl>
            <Button ref={editRef} variant="secondary" aria-label="Edit name and email" onClick={() => open('profile')}>
              Edit
            </Button>
          </div>
        )}
      </div>

      <div className={BLOCK_CLASS}>
        <h3 className="card-title mb-0 text-xl">Password</h3>
        {editing === 'password' ? (
          <PasswordForm
            onSaved={() => close('password', 'Your password has been changed.')}
            onCancel={() => close('password')}
          />
        ) : (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="font-mono text-[16px]">
              <span aria-hidden="true">••••••••</span>
              <span className="sr-only">Hidden</span>
            </p>
            <Button ref={passwordRef} variant="secondary" onClick={() => open('password')}>
              Change password
            </Button>
          </div>
        )}
      </div>
    </Blueprint>
  )
}
