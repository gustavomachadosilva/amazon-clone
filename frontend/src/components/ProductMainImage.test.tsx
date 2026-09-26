import { fireEvent, render, screen } from '@testing-library/react'
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

  it('keeps the static placeholder when the product has no image', () => {
    render(<ProductMainImage name="Wireless Headphones" />)
    expect(screen.getByText('Main photo')).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})
