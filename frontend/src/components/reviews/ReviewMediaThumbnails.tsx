import { useMemo, useState } from 'react'
import type { ReviewMedia } from '../../services/api'
import { buildReviewMediaItems } from '../../lib/reviewMedia'
import ReviewMediaLightbox from './ReviewMediaLightbox'
import ReviewMediaThumbnail from './ReviewMediaThumbnail'

interface ReviewMediaThumbnailsProps {
  media: ReviewMedia[]
  authorName: string
}

export default function ReviewMediaThumbnails({ media, authorName }: ReviewMediaThumbnailsProps) {
  const [openIndex, setOpenIndex] = useState<number | null>(null)
  const items = useMemo(() => buildReviewMediaItems(media, authorName), [media, authorName])

  if (items.length === 0) return null

  return (
    <>
      <ul aria-label={`Photos and videos from ${authorName}'s review`} className="mb-1.5 mt-2 flex flex-wrap gap-2">
        {items.map((item, i) => (
          <li key={item.media.id}>
            <ReviewMediaThumbnail item={item} onOpen={() => setOpenIndex(i)} />
          </li>
        ))}
      </ul>
      {openIndex !== null && (
        <ReviewMediaLightbox
          items={items}
          index={openIndex}
          onIndexChange={setOpenIndex}
          onClose={() => setOpenIndex(null)}
          label={`Photos and videos from ${authorName}'s review`}
        />
      )}
    </>
  )
}
