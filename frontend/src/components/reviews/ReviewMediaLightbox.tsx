import { useEffect, useRef, type KeyboardEvent, type MouseEvent, type PointerEvent } from 'react'
import { ChevronLeft, ChevronRight, X } from 'lucide-react'
import { resolveApiUrl } from '../../services/api'
import type { ReviewMediaItem } from '../../lib/reviewMedia'

interface ReviewMediaLightboxProps {
  items: ReviewMediaItem[]
  index: number
  onIndexChange: (index: number) => void
  onClose: () => void
  // Accessible name of the dialog, e.g. "Photos and videos from Ana's review".
  label: string
}

const CONTROL_CLASS =
  'flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-paper-900/60 text-white transition-colors hover:bg-paper-900/85 aria-disabled:cursor-default aria-disabled:opacity-40 aria-disabled:hover:bg-paper-900/60'

// Clicks on these elements (the empty area around the media) close the lightbox.
function isBackdrop(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && target.dataset.lightboxBackdrop === 'true'
}

export default function ReviewMediaLightbox({
  items,
  index,
  onIndexChange,
  onClose,
  label,
}: ReviewMediaLightboxProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  // Where the current click started: dragging a video's seek bar out onto the backdrop must not
  // count as a backdrop click.
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

  const count = items.length
  const current = items[Math.min(Math.max(index, 0), count - 1)]
  const hasPrevious = index > 0
  const hasNext = index < count - 1

  function goTo(nextIndex: number) {
    if (nextIndex >= 0 && nextIndex < count) onIndexChange(nextIndex)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDialogElement>) {
    // Arrow keys on a focused video seek it; leave them alone.
    if (event.target instanceof HTMLVideoElement) return
    if (event.key === 'ArrowLeft') {
      event.preventDefault()
      goTo(index - 1)
    } else if (event.key === 'ArrowRight') {
      event.preventDefault()
      goTo(index + 1)
    }
  }

  function handlePointerDown(event: PointerEvent<HTMLDialogElement>) {
    pointerDownOnBackdrop.current = isBackdrop(event.target)
  }

  function handleClick(event: MouseEvent<HTMLDialogElement>) {
    // Keyboard-triggered clicks have no pointerdown, but they never land on the backdrop.
    if (isBackdrop(event.target) && pointerDownOnBackdrop.current) onClose()
    pointerDownOnBackdrop.current = false
  }

  if (!current) return null
  const src = resolveApiUrl(current.media.url)

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
      onKeyDown={handleKeyDown}
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
            className={CONTROL_CLASS}
            onClick={onClose}
          >
            <X size={22} strokeWidth={1.5} />
          </button>
        </div>

        <div
          data-lightbox-backdrop="true"
          className="flex min-h-0 w-full flex-1 flex-col items-center justify-center gap-2"
        >
          {current.media.type === 'VIDEO' ? (
            <video
              key={current.media.id}
              src={src}
              controls
              preload="metadata"
              playsInline
              aria-label={current.alt}
              className="max-h-[75dvh] max-w-full bg-black"
            />
          ) : (
            <img
              key={current.media.id}
              src={src}
              alt={current.alt}
              className="max-h-[75dvh] max-w-full object-contain"
            />
          )}
          {current.caption && (
            <p className="max-w-[70ch] text-center text-[15px] text-paper-200">{current.caption}</p>
          )}
        </div>

        {/* Below the media on every viewport, so it never covers the native video controls. */}
        <div className="flex items-center gap-4">
          {count > 1 && (
            <button
              type="button"
              aria-label="Previous photo or video"
              aria-disabled={!hasPrevious}
              className={CONTROL_CLASS}
              onClick={() => goTo(index - 1)}
            >
              <ChevronLeft size={24} strokeWidth={1.5} />
            </button>
          )}
          <p aria-live="polite" className="readout min-w-[4.5rem] text-center text-[15px] text-white">
            {index + 1} of {count}
          </p>
          {count > 1 && (
            <button
              type="button"
              aria-label="Next photo or video"
              aria-disabled={!hasNext}
              className={CONTROL_CLASS}
              onClick={() => goTo(index + 1)}
            >
              <ChevronRight size={24} strokeWidth={1.5} />
            </button>
          )}
        </div>
      </div>
    </dialog>
  )
}
