import { fireEvent, render, screen, within } from '@testing-library/react'
import ProductMainImage from './ProductMainImage'

describe('ProductMainImage', () => {
  it('opens the zoom viewer from the photo and returns focus to it on Esc', () => {
    render(<ProductMainImage name="Wireless Headphones" imageUrl="/img/headphones.jpg" />)
    const trigger = screen.getByRole('button', { name: 'View larger image of Wireless Headphones' })
    expect(trigger).toHaveClass('cursor-zoom-in')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    fireEvent.click(trigger)
    const dialog = screen.getByRole('dialog', { name: 'Photo of Wireless Headphones' })
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus()
    expect(document.documentElement.style.overflow).toBe('hidden')

    fireEvent(dialog, new Event('cancel', { cancelable: true }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
    expect(document.documentElement.style.overflow).toBe('')
  })

  it('closes from the Close button', () => {
    render(<ProductMainImage name="Wireless Headphones" imageUrl="/img/headphones.jpg" />)
    const trigger = screen.getByRole('button', { name: 'View larger image of Wireless Headphones' })
    fireEvent.click(trigger)
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('downloads the full-resolution Amazon image only once the viewer opens', () => {
    const thumb = 'https://m.media-amazon.com/images/I/81k57a1xhuL._AC_UL320_.jpg'
    const full = 'https://m.media-amazon.com/images/I/81k57a1xhuL.jpg'
    render(<ProductMainImage name="Wireless Headphones" imageUrl={thumb} />)
    expect(document.querySelector(`img[src="${full}"]`)).toBeNull()

    const trigger = screen.getByRole('button', { name: 'View larger image of Wireless Headphones' })
    fireEvent.click(trigger)
    expect(document.querySelector(`img[hidden][src="${full}"]`)).toBeInTheDocument()
    const dialog = screen.getByRole('dialog', { name: 'Photo of Wireless Headphones' })
    expect(within(dialog).getByAltText('Wireless Headphones')).toHaveAttribute('src', thumb)
    expect(trigger.querySelector('img')).toHaveAttribute('src', thumb)
  })

  it('does not preload anything for a non-Amazon image', () => {
    render(<ProductMainImage name="Wireless Headphones" imageUrl="/img/headphones.jpg" />)
    fireEvent.click(screen.getByRole('button', { name: 'View larger image of Wireless Headphones' }))
    expect(document.querySelector('img[hidden]')).toBeNull()
  })

  it('keeps the static placeholder when the product has no image', () => {
    render(<ProductMainImage name="Wireless Headphones" />)
    expect(screen.getByText('Main photo')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})
