import type { MouseEvent } from 'react'
import { Play } from 'lucide-react'
import { resolveApiUrl } from '../../services/api'
import { REVIEW_THUMBNAIL_CLASS, type ReviewMediaItem } from '../../lib/reviewMedia'

interface ReviewMediaThumbnailProps {
  item: ReviewMediaItem
  onOpen: () => void
}

export default function ReviewMediaThumbnail({ item, onOpen }: ReviewMediaThumbnailProps) {
  const { media } = item
  const src = resolveApiUrl(media.url)

  function handleClick(event: MouseEvent<HTMLButtonElement>) {
    // Safari doesn't focus buttons on click; the lightbox returns focus to whatever was focused
    // when it opened, so make sure that's this thumbnail.
    event.currentTarget.focus()
    onOpen()
  }

  return (
    <button
      type="button"
      className={REVIEW_THUMBNAIL_CLASS}
      aria-haspopup="dialog"
      aria-label={media.type === 'VIDEO' ? item.thumbnailLabel : undefined}
      onClick={handleClick}
    >
      {media.type === 'VIDEO' ? (
        <>
          {/* #t=0.1 makes browsers paint a first frame as the poster. */}
          <video
            src={`${src}#t=0.1`}
            preload="metadata"
            muted
            playsInline
            aria-hidden="true"
            tabIndex={-1}
            className="pointer-events-none h-full w-full object-cover"
          />
          <span aria-hidden="true" className="absolute inset-0 flex items-center justify-center">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-paper-900/70 text-white">
              <Play size={16} strokeWidth={1.5} fill="currentColor" />
            </span>
          </span>
        </>
      ) : (
        <img
          src={src}
          alt={item.alt}
          loading="lazy"
          decoding="async"
          className="h-full w-full object-cover"
        />
      )}
    </button>
  )
}
