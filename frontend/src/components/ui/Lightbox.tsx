import { useEffect, useRef, type KeyboardEvent, type MouseEvent, type PointerEvent, type ReactNode } from 'react'
import { X } from 'lucide-react'
import { LIGHTBOX_CONTROL_CLASS } from './lightboxStyles'

interface LightboxProps {
  // Accessible name of the dialog, e.g. "Photos and videos from Ana's review".
  label: string
  onClose: () => void
  onKeyDown?: (event: KeyboardEvent<HTMLDialogElement>) => void
  // Rendered below the Close row, inside the full-screen column.
  children: ReactNode
}

// Clicks on these elements (the empty area around the content) close the lightbox. Children opt
// their own empty areas in with `data-lightbox-backdrop="true"`.
function isBackdrop(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && target.dataset.lightboxBackdrop === 'true'
}

// Full-screen modal shell shared by the review media lightbox and the product image viewer:
// native modal <dialog>, page scroll locked, focus on Close while open and back where it was on
// exit; Esc, Close and a backdrop click all call `onClose`.
export default function Lightbox({ label, onClose, onKeyDown, children }: LightboxProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  // Where the current click started: dragging a video's seek bar (or a zoomed image) out onto
  // the backdrop must not count as a backdrop click.
  const pointerDownOnBackdrop = useRef(false)

  // Native modal <dialog>: the rest of the page goes inert, so focus stays inside it.
  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const root = document.documentElement
    const previousOverflow = root.style.overflow
    root.style.overflow = 'hidden'
    if (!dialog.open) dialog.showModal()
    closeButtonRef.current?.focus()
    return () => {
      root.style.overflow = previousOverflow
      if (dialog.open) dialog.close()
      previouslyFocused?.focus()
    }
  }, [])

  function handlePointerDown(event: PointerEvent<HTMLDialogElement>) {
    pointerDownOnBackdrop.current = isBackdrop(event.target)
  }

  function handleClick(event: MouseEvent<HTMLDialogElement>) {
    // Keyboard-triggered clicks have no pointerdown, but they never land on the backdrop.
    if (isBackdrop(event.target) && pointerDownOnBackdrop.current) onClose()
    pointerDownOnBackdrop.current = false
  }

  return (
    <dialog
      ref={dialogRef}
      aria-label={label}
      data-lightbox-backdrop="true"
      className="m-0 h-full max-h-none w-full max-w-none border-0 bg-transparent p-0 backdrop:bg-paper-900/85"
      onCancel={(event) => {
        // Esc: let React unmount the dialog instead of the browser closing it behind our back.
        event.preventDefault()
        onClose()
      }}
      onKeyDown={onKeyDown}
      onPointerDown={handlePointerDown}
      onClick={handleClick}
    >
      <div
        data-lightbox-backdrop="true"
        className="flex h-full w-full flex-col items-center gap-3 px-4 py-4 text-white"
      >
        <div data-lightbox-backdrop="true" className="flex w-full justify-end">
          <button
            ref={closeButtonRef}
            type="button"
            aria-label="Close"
            className={LIGHTBOX_CONTROL_CLASS}
            onClick={onClose}
          >
            <X size={22} strokeWidth={1.5} />
          </button>
        </div>

        {children}
      </div>
    </dialog>
  )
}
