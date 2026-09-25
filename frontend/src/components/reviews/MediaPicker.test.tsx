import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'
import MediaPicker from './MediaPicker'

const createObjectURL = vi.fn()
const revokeObjectURL = vi.fn()

function png(name: string) {
  return new File(['x'], name, { type: 'image/png' })
}

function pick(files: File[]) {
  fireEvent.change(screen.getByTestId('media-input'), { target: { files } })
}

function lastNames(onChange: ReturnType<typeof vi.fn>) {
  return (onChange.mock.lastCall?.[0] as File[]).map((f) => f.name)
}

beforeEach(() => {
  let n = 0
  createObjectURL.mockReset().mockImplementation(() => `blob:test/${n++}`)
  revokeObjectURL.mockReset()
  URL.createObjectURL = createObjectURL
  URL.revokeObjectURL = revokeObjectURL
})

describe('MediaPicker', () => {
  it('keeps at most 5 files and reports the rest without discarding the accepted ones', () => {
    const onChange = vi.fn()
    render(<MediaPicker onChange={onChange} />)

    pick(Array.from({ length: 7 }, (_, i) => png(`p${i}.png`)))

    expect(screen.getAllByRole('img')).toHaveLength(5)
    expect(screen.getByText('5 of 5')).toBeInTheDocument()
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('p5.png')
    expect(alert).toHaveTextContent('p6.png')
    expect(lastNames(onChange)).toEqual(['p0.png', 'p1.png', 'p2.png', 'p3.png', 'p4.png'])
    expect(screen.getByRole('button', { name: 'Add photos or videos' })).toBeDisabled()
  })

  it('rejects an invalid file inline and keeps the valid one', () => {
    const onChange = vi.fn()
    render(<MediaPicker onChange={onChange} />)

    pick([png('ok.png'), new File(['x'], 'anim.gif', { type: 'image/gif' })])

    expect(screen.getByText('1 of 5')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('anim.gif: Only JPEG, PNG, WebP, MP4 or WebM files are allowed')
    expect(lastNames(onChange)).toEqual(['ok.png'])
  })

  it('adds dropped files', () => {
    render(<MediaPicker onChange={vi.fn()} />)

    fireEvent.drop(screen.getByTestId('media-drop-zone'), { dataTransfer: { files: [png('dropped.png')] } })

    expect(screen.getByRole('img', { name: 'dropped.png' })).toBeInTheDocument()
  })

  it('shows a video thumbnail labelled with the file name', () => {
    render(<MediaPicker onChange={vi.fn()} />)

    pick([new File(['x'], 'clip.mp4', { type: 'video/mp4' })])

    expect(screen.getByLabelText('clip.mp4').tagName).toBe('VIDEO')
  })

  it('removes a file, revokes its URL and moves focus to the next Remove button', () => {
    const onChange = vi.fn()
    render(<MediaPicker onChange={onChange} />)
    pick([png('a.png'), png('b.png')])

    fireEvent.click(screen.getByRole('button', { name: 'Remove a.png' }))

    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test/0')
    expect(screen.queryByRole('img', { name: 'a.png' })).not.toBeInTheDocument()
    expect(screen.getByText('1 of 5')).toBeInTheDocument()
    expect(lastNames(onChange)).toEqual(['b.png'])
    expect(screen.getByRole('button', { name: 'Remove b.png' })).toHaveFocus()

    fireEvent.click(screen.getByRole('button', { name: 'Remove b.png' }))
    expect(screen.getByRole('button', { name: 'Add photos or videos' })).toHaveFocus()
  })

  it('revokes the remaining URLs on unmount', () => {
    const { unmount } = render(<MediaPicker onChange={vi.fn()} />)
    pick([png('a.png'), png('b.png')])

    unmount()

    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test/0')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test/1')
  })

  it('disables its controls and ignores drops while disabled', () => {
    render(<MediaPicker onChange={vi.fn()} disabled />)

    fireEvent.drop(screen.getByTestId('media-drop-zone'), { dataTransfer: { files: [png('x.png')] } })

    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add photos or videos' })).toBeDisabled()
  })
})
