import { useId, useMemo, useState } from 'react'
import type { ReviewView } from '../../services/api'
import { REVIEW_THUMBNAIL_CLASS, buildReviewMediaItems } from '../../lib/reviewMedia'
import ReviewMediaLightbox from './ReviewMediaLightbox'
import ReviewMediaThumbnail from './ReviewMediaThumbnail'

interface ReviewsWithImagesProps {
  reviews: ReviewView[]
}

const MAX_VISIBLE = 8

// Every review's photos and videos in one strip at the top of the reviews list.
export default function ReviewsWithImages({ reviews }: ReviewsWithImagesProps) {
  const headingId = useId()
  const [openIndex, setOpenIndex] = useState<number | null>(null)
  const items = useMemo(
    () =>
      reviews.flatMap((review) =>
        buildReviewMediaItems(review.media, review.authorName, `${review.authorName} — ${review.title}`),
      ),
    [reviews],
  )

  if (items.length === 0) return null

  const visible = items.slice(0, MAX_VISIBLE)
  const hiddenCount = items.length - visible.length

  return (
    <section aria-labelledby={headingId}>
      <h3 id={headingId} className="mb-2 text-[18px]">
        Reviews with images
      </h3>
      <ul className="flex flex-wrap gap-2">
        {visible.map((item, i) => (
          <li key={item.media.id}>
            <ReviewMediaThumbnail item={item} onOpen={() => setOpenIndex(i)} />
          </li>
        ))}
        {hiddenCount > 0 && (
          <li>
            <button
              type="button"
              aria-haspopup="dialog"
              aria-label={`See ${hiddenCount} more photos and videos`}
              className={`${REVIEW_THUMBNAIL_CLASS} readout flex items-center justify-center text-[17px] text-paper-800`}
              onClick={(event) => {
                event.currentTarget.focus()
                setOpenIndex(MAX_VISIBLE)
              }}
            >
              +{hiddenCount}
            </button>
          </li>
        )}
      </ul>
      {openIndex !== null && openIndex < items.length && (
        <ReviewMediaLightbox
          items={items}
          index={openIndex}
          onIndexChange={setOpenIndex}
          onClose={() => setOpenIndex(null)}
          label="Customer photos and videos"
        />
      )}
    </section>
  )
}
