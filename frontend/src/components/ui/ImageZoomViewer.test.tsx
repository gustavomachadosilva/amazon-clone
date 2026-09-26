import { fireEvent, render, screen } from '@testing-library/react'
import ImageZoomViewer from './ImageZoomViewer'

function renderViewer() {
  const onClose = vi.fn()
  render(<ImageZoomViewer src="/img/headphones.jpg" alt="Wireless Headphones" label="Photo of Wireless Headphones" onClose={onClose} />)
  return { onClose, dialog: screen.getByRole('dialog', { name: 'Photo of Wireless Headphones' }) }
}

// The zoom level readout, e.g. "250%".
function readout() {
  return screen.getByText(/^\d+%$/)
}

function image() {
  return screen.getByAltText('Wireless Headphones')
}

describe('ImageZoomViewer', () => {
  it('opens as a modal dialog at 100% with Close focused', () => {
    const { dialog } = renderViewer()
    expect(dialog).toHaveAttribute('open')
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus()
    expect(readout()).toHaveTextContent('100%')
    expect(readout()).toHaveAttribute('aria-live', 'polite')
    expect(image()).toHaveAttribute('src', '/img/headphones.jpg')
  })

  it('zooms in by steps up to 400% and disables Zoom in at the limit', () => {
    renderViewer()
    const zoomIn = screen.getByRole('button', { name: 'Zoom in' })
    fireEvent.click(zoomIn)
    expect(readout()).toHaveTextContent('150%')
    for (let i = 0; i < 10; i++) fireEvent.click(zoomIn)
    expect(readout()).toHaveTextContent('400%')
    expect(zoomIn).toHaveAttribute('aria-disabled', 'true')
    expect(image().style.transform).toContain('scale(4)')
  })

  it('zooms out down to 100% and disables Zoom out and Reset there', () => {
    renderViewer()
    const zoomOut = screen.getByRole('button', { name: 'Zoom out' })
    expect(zoomOut).toHaveAttribute('aria-disabled', 'true')
    fireEvent.click(zoomOut)
    expect(readout()).toHaveTextContent('100%')
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }))
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }))
    expect(readout()).toHaveTextContent('200%')
    fireEvent.click(zoomOut)
    expect(readout()).toHaveTextContent('150%')
    fireEvent.click(zoomOut)
    fireEvent.click(zoomOut)
    expect(readout()).toHaveTextContent('100%')
    expect(zoomOut).toHaveAttribute('aria-disabled', 'true')
    expect(screen.getByRole('button', { name: 'Reset zoom' })).toHaveAttribute('aria-disabled', 'true')
  })

  it('resets to 100%', () => {
    renderViewer()
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }))
    fireEvent.click(screen.getByRole('button', { name: 'Zoom in' }))
    const reset = screen.getByRole('button', { name: 'Reset zoom' })
    expect(reset).toHaveAttribute('aria-disabled', 'false')
    fireEvent.click(reset)
    expect(readout()).toHaveTextContent('100%')
  })

  it('zooms with the +, - and 0 keys but leaves Ctrl/Cmd shortcuts to the browser', () => {
    const { dialog } = renderViewer()
    fireEvent.keyDown(dialog, { key: '+' })
    expect(readout()).toHaveTextContent('150%')
    fireEvent.keyDown(dialog, { key: '=' })
    expect(readout()).toHaveTextContent('200%')
    fireEvent.keyDown(dialog, { key: '-' })
    expect(readout()).toHaveTextContent('150%')
    fireEvent.keyDown(dialog, { key: '0' })
    expect(readout()).toHaveTextContent('100%')
    fireEvent.keyDown(dialog, { key: '+', ctrlKey: true })
    fireEvent.keyDown(dialog, { key: '=', metaKey: true })
    expect(readout()).toHaveTextContent('100%')
  })

  it('toggles between fit and 250% when the image is clicked, without closing', () => {
    const { onClose } = renderViewer()
    fireEvent.click(image())
    expect(readout()).toHaveTextContent('250%')
    fireEvent.click(image())
    expect(readout()).toHaveTextContent('100%')
    expect(onClose).not.toHaveBeenCalled()
  })

  it('does not toggle zoom at the end of a drag', () => {
    renderViewer()
    const viewport = image().parentElement as HTMLElement
    fireEvent.pointerDown(viewport, { pointerId: 1, pointerType: 'mouse', button: 0, clientX: 10, clientY: 10 })
    fireEvent.pointerMove(viewport, { pointerId: 1, pointerType: 'mouse', clientX: 60, clientY: 40 })
    fireEvent.pointerUp(viewport, { pointerId: 1, pointerType: 'mouse', clientX: 60, clientY: 40 })
    fireEvent.click(viewport, { clientX: 60, clientY: 40 })
    expect(readout()).toHaveTextContent('100%')
  })

  it('zooms in on wheel up and back out on wheel down', () => {
    renderViewer()
    const viewport = image().parentElement as HTMLElement
    fireEvent.wheel(viewport, { deltaY: -200 })
    expect(readout()).toHaveTextContent('135%')
    fireEvent.wheel(viewport, { deltaY: 1000 })
    expect(readout()).toHaveTextContent('100%')
  })

  it('closes on Esc, the Close button and a backdrop click', () => {
    const { onClose, dialog } = renderViewer()
    fireEvent(dialog, new Event('cancel', { cancelable: true }))
    expect(onClose).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: 'Close' }))
    expect(onClose).toHaveBeenCalledTimes(2)
    fireEvent.pointerDown(dialog)
    fireEvent.click(dialog)
    expect(onClose).toHaveBeenCalledTimes(3)
  })

  it('does not close when a press on the image ends on the backdrop', () => {
    const { onClose, dialog } = renderViewer()
    fireEvent.pointerDown(image())
    fireEvent.click(dialog)
    expect(onClose).not.toHaveBeenCalled()
  })
})
