import { render, screen } from '@testing-library/react'
import Placeholder from './Placeholder'

describe('Placeholder', () => {
  it('renders an image sized by classes, not by inline width/height', () => {
    render(<Placeholder label="x" src="/a.png" className="w-[56px]" />)

    const img = screen.getByRole('img', { name: 'x' })
    expect(img).toHaveClass('ph-image')
    expect(img).toHaveClass('w-[56px]')
    expect(img.style.width).toBe('')
    expect(img.style.height).toBe('')
    expect(img.style.aspectRatio).toBe('1/1')
  })

  it('renders the label when there is no src', () => {
    render(<Placeholder label="Main photo" />)

    expect(screen.getByText('Main photo')).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
})
