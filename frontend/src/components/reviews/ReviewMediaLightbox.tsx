import type { KeyboardEvent } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import Lightbox from '../ui/Lightbox'
import { LIGHTBOX_CONTROL_CLASS } from '../ui/lightboxStyles'
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

export default function ReviewMediaLightbox({
  items,
  index,
  onIndexChange,
  onClose,
  label,
}: ReviewMediaLightboxProps) {
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

  if (!current) return null
  const src = resolveApiUrl(current.media.url)

  return (
    <Lightbox label={label} onClose={onClose} onKeyDown={handleKeyDown}>
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
            className={LIGHTBOX_CONTROL_CLASS}
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
            className={LIGHTBOX_CONTROL_CLASS}
            onClick={() => goTo(index + 1)}
          >
            <ChevronRight size={24} strokeWidth={1.5} />
          </button>
        )}
      </div>
    </Lightbox>
  )
}
