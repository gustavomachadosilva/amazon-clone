// Client-side mirror of the backend's review-media rules, so a bad file is refused before the
// upload starts. Keep in sync with ReviewServiceImpl.MAX_FILES / ALLOWED_MSG and
// ReviewMediaTypeDetector.MAX_IMAGE_BYTES / MAX_VIDEO_BYTES (backend/.../reviews/service). The
// backend still has the final say (it checks magic bytes, not the browser-reported type).

export const MAX_REVIEW_MEDIA_FILES = 5
export const MAX_REVIEW_IMAGE_BYTES = 5 * 1024 * 1024
export const MAX_REVIEW_VIDEO_BYTES = 50 * 1024 * 1024

export const REVIEW_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const
export const REVIEW_VIDEO_TYPES = ['video/mp4', 'video/webm'] as const
export const REVIEW_MEDIA_ACCEPT = [...REVIEW_IMAGE_TYPES, ...REVIEW_VIDEO_TYPES].join(',')

export const REVIEW_MEDIA_MESSAGES = {
  tooMany: `A review can have at most ${MAX_REVIEW_MEDIA_FILES} photos or videos`,
  notAllowed: 'Only JPEG, PNG, WebP, MP4 or WebM files are allowed',
  empty: 'Empty files are not allowed',
  imageTooBig: 'Images must be at most 5 MB',
  videoTooBig: 'Videos must be at most 50 MB',
} as const

export type MediaKind = 'image' | 'video'

export type MediaValidation = { ok: true; kind: MediaKind } | { ok: false; reason: string }

export function mediaKindOf(file: File): MediaKind | null {
  if ((REVIEW_IMAGE_TYPES as readonly string[]).includes(file.type)) return 'image'
  if ((REVIEW_VIDEO_TYPES as readonly string[]).includes(file.type)) return 'video'
  return null
}

// Same order as the backend: empty first, then type, then the size limit for that type.
export function validateReviewMediaFile(file: File): MediaValidation {
  if (file.size <= 0) return { ok: false, reason: REVIEW_MEDIA_MESSAGES.empty }
  const kind = mediaKindOf(file)
  if (!kind) return { ok: false, reason: REVIEW_MEDIA_MESSAGES.notAllowed }
  if (kind === 'image' && file.size > MAX_REVIEW_IMAGE_BYTES) {
    return { ok: false, reason: REVIEW_MEDIA_MESSAGES.imageTooBig }
  }
  if (kind === 'video' && file.size > MAX_REVIEW_VIDEO_BYTES) {
    return { ok: false, reason: REVIEW_MEDIA_MESSAGES.videoTooBig }
  }
  return { ok: true, kind }
}
