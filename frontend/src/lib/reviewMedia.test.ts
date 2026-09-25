import {
  MAX_REVIEW_IMAGE_BYTES,
  MAX_REVIEW_VIDEO_BYTES,
  REVIEW_MEDIA_ACCEPT,
  REVIEW_MEDIA_MESSAGES,
  validateReviewMediaFile,
} from './reviewMedia'

// Builds a File that reports `size` bytes without allocating them.
function fakeFile(name: string, type: string, size: number): File {
  const file = new File([], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('validateReviewMediaFile', () => {
  it.each(['image/jpeg', 'image/png', 'image/webp'])('accepts a %s image of exactly 5 MiB', (type) => {
    expect(validateReviewMediaFile(fakeFile('a', type, MAX_REVIEW_IMAGE_BYTES))).toEqual({ ok: true, kind: 'image' })
  })

  it('rejects an image one byte over 5 MiB', () => {
    expect(validateReviewMediaFile(fakeFile('a.png', 'image/png', MAX_REVIEW_IMAGE_BYTES + 1))).toEqual({
      ok: false,
      reason: REVIEW_MEDIA_MESSAGES.imageTooBig,
    })
  })

  it.each(['video/mp4', 'video/webm'])('accepts a %s video of exactly 50 MiB', (type) => {
    expect(validateReviewMediaFile(fakeFile('v', type, MAX_REVIEW_VIDEO_BYTES))).toEqual({ ok: true, kind: 'video' })
  })

  it('rejects a video one byte over 50 MiB', () => {
    expect(validateReviewMediaFile(fakeFile('v.mp4', 'video/mp4', MAX_REVIEW_VIDEO_BYTES + 1))).toEqual({
      ok: false,
      reason: REVIEW_MEDIA_MESSAGES.videoTooBig,
    })
  })

  it.each(['image/gif', 'image/svg+xml', 'application/pdf', ''])('rejects type "%s"', (type) => {
    expect(validateReviewMediaFile(fakeFile('x', type, 10))).toEqual({
      ok: false,
      reason: REVIEW_MEDIA_MESSAGES.notAllowed,
    })
  })

  it('rejects an empty file', () => {
    expect(validateReviewMediaFile(fakeFile('a.png', 'image/png', 0))).toEqual({
      ok: false,
      reason: REVIEW_MEDIA_MESSAGES.empty,
    })
  })

  it('builds the accept attribute from the allowed types', () => {
    expect(REVIEW_MEDIA_ACCEPT).toBe('image/jpeg,image/png,image/webp,video/mp4,video/webm')
  })
})
