import type { ReviewMedia } from '../services/api'

// Shared by the thumbnail buttons and the "+N" tile in the "Reviews with images" block.
export const REVIEW_THUMBNAIL_CLASS =
  'relative block h-[72px] w-[72px] overflow-hidden border border-divider bg-surface transition-colors hover:border-accent focus-visible:border-accent'

// One photo or video as the thumbnails and the lightbox show it.
export interface ReviewMediaItem {
  media: ReviewMedia
  // Image alt / video accessible name, e.g. "Photo 1 from Ana's review".
  alt: string
  // Accessible name for the thumbnail button (videos get a "Play …" label; photos use their alt).
  thumbnailLabel: string
  caption?: string
}

// Photos and videos are numbered separately, so a review with two photos and a video reads
// "Photo 1", "Photo 2", "Video 1".
export function buildReviewMediaItems(
  media: ReviewMedia[],
  authorName: string,
  caption?: string,
): ReviewMediaItem[] {
  let photoCount = 0
  let videoCount = 0
  return media.map((item) => {
    if (item.type === 'VIDEO') {
      videoCount += 1
      return {
        media: item,
        alt: `Video ${videoCount} from ${authorName}'s review`,
        thumbnailLabel: `Play video ${videoCount} from ${authorName}'s review`,
        caption,
      }
    }
    photoCount += 1
    const alt = `Photo ${photoCount} from ${authorName}'s review`
    return { media: item, alt, thumbnailLabel: alt, caption }
  })
}
