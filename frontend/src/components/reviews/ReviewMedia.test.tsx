import { fireEvent, render, screen, within } from '@testing-library/react'
import { beforeAll } from 'vitest'
import type { ReviewMedia, ReviewView } from '../../services/api'
import ReviewMediaThumbnails from './ReviewMediaThumbnails'
import ReviewsWithImages from './ReviewsWithImages'

// jsdom doesn't implement the modal <dialog> API.
beforeAll(() => {
  if (!HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.setAttribute('open', '')
    }
  }
  if (!HTMLDialogElement.prototype.close) {
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.removeAttribute('open')
    }
  }
})

const PHOTO_1: ReviewMedia = { id: 1, type: 'IMAGE', url: '/api/reviews/media/1' }
const PHOTO_2: ReviewMedia = { id: 2, type: 'IMAGE', url: '/api/reviews/media/2' }
const VIDEO_1: ReviewMedia = { id: 3, type: 'VIDEO', url: '/api/reviews/media/3' }

function review(overrides: Partial<ReviewView>): ReviewView {
  return {
    id: 1,
    productId: 10,
    authorId: 100,
    authorName: 'Ana',
    stars: 5,
    title: 'Great',
    text: 'Works well.',
    helpfulCount: 0,
    createdAt: '2026-01-01T00:00:00Z',
    media: [],
    ...overrides,
  }
}

function renderThumbnails(media: ReviewMedia[]) {
  return render(<ReviewMediaThumbnails media={media} authorName="Ana" />)
}

function openThumbnail(name: string) {
  const thumbnail = screen.getByRole('button', { name })
  fireEvent.click(thumbnail)
  return thumbnail
}

describe('ReviewMediaThumbnails', () => {
  it('renders nothing when the review has no media', () => {
    renderThumbnails([])
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('renders lazy photo thumbnails with descriptive alt and an API-prefixed src', () => {
    renderThumbnails([PHOTO_1, PHOTO_2])
    const list = screen.getByRole('list', { name: "Photos and videos from Ana's review" })
    const img = within(list).getByAltText("Photo 2 from Ana's review")
    expect(img).toHaveAttribute('loading', 'lazy')
    expect(img).toHaveAttribute('src', 'http://localhost:8080/api/reviews/media/2')
  })

  it('renders video thumbnails with a play label and metadata-only preload', () => {
    const { container } = renderThumbnails([PHOTO_1, VIDEO_1])
    expect(screen.getByRole('button', { name: "Play video 1 from Ana's review" })).toBeInTheDocument()
    const video = container.querySelector('video')
    expect(video).toHaveAttribute('preload', 'metadata')
    expect(video).toHaveAttribute('src', 'http://localhost:8080/api/reviews/media/3#t=0.1')
  })

  it('opens the lightbox with the large image when a thumbnail is clicked', () => {
    renderThumbnails([PHOTO_1, PHOTO_2])
    openThumbnail("Photo 2 from Ana's review")
    const dialog = screen.getByRole('dialog', { name: "Photos and videos from Ana's review" })
    expect(within(dialog).getByAltText("Photo 2 from Ana's review")).toBeInTheDocument()
    expect(within(dialog).getByText('2 of 2')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Close' })).toHaveFocus()
  })

  it('navigates with the arrow keys and the previous/next buttons, without wrapping', () => {
    renderThumbnails([PHOTO_1, PHOTO_2, VIDEO_1])
    openThumbnail("Photo 1 from Ana's review")
    const dialog = screen.getByRole('dialog')

    fireEvent.keyDown(dialog, { key: 'ArrowRight' })
    expect(within(dialog).getByAltText("Photo 2 from Ana's review")).toBeInTheDocument()

    fireEvent.click(within(dialog).getByRole('button', { name: 'Next photo or video' }))
    const video = within(dialog).getByLabelText("Video 1 from Ana's review")
    expect(video.tagName).toBe('VIDEO')
    expect(video).toHaveAttribute('controls')
    expect(video).toHaveAttribute('preload', 'metadata')
    expect(within(dialog).getByRole('button', { name: 'Next photo or video' })).toHaveAttribute(
      'aria-disabled',
      'true',
    )

    fireEvent.click(within(dialog).getByRole('button', { name: 'Next photo or video' }))
    expect(within(dialog).getByText('3 of 3')).toBeInTheDocument()

    fireEvent.click(within(dialog).getByRole('button', { name: 'Previous photo or video' }))
    fireEvent.keyDown(dialog, { key: 'ArrowLeft' })
    expect(within(dialog).getByAltText("Photo 1 from Ana's review")).toBeInTheDocument()
    expect(within(dialog).getByText('1 of 3')).toBeInTheDocument()
  })

  it('leaves arrow keys to a focused video', () => {
    renderThumbnails([VIDEO_1, PHOTO_1])
    openThumbnail("Play video 1 from Ana's review")
    const dialog = screen.getByRole('dialog')
    fireEvent.keyDown(within(dialog).getByLabelText("Video 1 from Ana's review"), { key: 'ArrowRight' })
    expect(within(dialog).getByText('1 of 2')).toBeInTheDocument()
  })

  it('closes on Esc and returns focus to the thumbnail that opened it', () => {
    renderThumbnails([PHOTO_1, PHOTO_2])
    const thumbnail = openThumbnail("Photo 2 from Ana's review")
    fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(thumbnail).toHaveFocus()
    expect(document.documentElement.style.overflow).toBe('')
  })

  it('closes on a backdrop click but not on a click on the media', () => {
    renderThumbnails([PHOTO_1])
    const thumbnail = openThumbnail("Photo 1 from Ana's review")
    const dialog = screen.getByRole('dialog')

    const image = within(dialog).getByAltText("Photo 1 from Ana's review")
    fireEvent.pointerDown(image)
    fireEvent.click(image)
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    fireEvent.pointerDown(dialog)
    fireEvent.click(dialog)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(thumbnail).toHaveFocus()
  })

  it('hides previous/next when there is a single item', () => {
    renderThumbnails([PHOTO_1])
    openThumbnail("Photo 1 from Ana's review")
    expect(screen.queryByRole('button', { name: 'Next photo or video' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Previous photo or video' })).not.toBeInTheDocument()
  })
})

describe('ReviewsWithImages', () => {
  it('renders nothing when no review has media', () => {
    const { container } = render(<ReviewsWithImages reviews={[review({})]} />)
    expect(container).toBeEmptyDOMElement()
  })

  it("gathers every review's media and opens the \"+N\" tile at the first hidden item", () => {
    const manyPhotos: ReviewMedia[] = Array.from({ length: 9 }, (_, i) => ({
      id: 100 + i,
      type: 'IMAGE',
      url: `/api/reviews/media/${100 + i}`,
    }))
    render(
      <ReviewsWithImages
        reviews={[
          review({ id: 1, authorName: 'Ana', media: manyPhotos }),
          review({ id: 2, authorName: 'Bruno', title: 'Solid', media: [VIDEO_1] }),
        ]}
      />,
    )
    expect(screen.getByRole('heading', { name: 'Reviews with images' })).toBeInTheDocument()
    expect(screen.getAllByRole('img')).toHaveLength(8)

    fireEvent.click(screen.getByRole('button', { name: 'See 2 more photos and videos' }))
    const dialog = screen.getByRole('dialog', { name: 'Customer photos and videos' })
    expect(within(dialog).getByAltText("Photo 9 from Ana's review")).toBeInTheDocument()
    expect(within(dialog).getByText('9 of 10')).toBeInTheDocument()

    fireEvent.keyDown(dialog, { key: 'ArrowRight' })
    expect(within(dialog).getByLabelText("Video 1 from Bruno's review")).toBeInTheDocument()
    expect(within(dialog).getByText('Bruno — Solid')).toBeInTheDocument()
  })
})
