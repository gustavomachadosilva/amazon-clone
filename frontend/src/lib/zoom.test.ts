import {
  IDENTITY,
  MAX_SCALE,
  MIN_SCALE,
  TOGGLE_SCALE,
  clampPan,
  clampScale,
  formatZoom,
  stepScale,
  toggleAt,
  zoomAt,
} from './zoom'

const VIEWPORT = { w: 400, h: 300 }
const CONTENT = { w: 300, h: 300 }
const ZERO = { w: 0, h: 0 }

describe('clampScale', () => {
  it('keeps the level between 1× and 4×', () => {
    expect(clampScale(0.2)).toBe(MIN_SCALE)
    expect(clampScale(2)).toBe(2)
    expect(clampScale(9)).toBe(MAX_SCALE)
  })

  it('falls back to 1× on non-finite input', () => {
    expect(clampScale(Number.NaN)).toBe(MIN_SCALE)
    expect(clampScale(Number.POSITIVE_INFINITY)).toBe(MIN_SCALE)
  })
})

describe('clampPan', () => {
  it('lets the image move only as far as it overflows the viewport', () => {
    // 2× → 600×600 in a 400×300 viewport: 100px of slack horizontally, 150px vertically.
    expect(clampPan({ scale: 2, x: 500, y: -500 }, VIEWPORT, CONTENT)).toEqual({ scale: 2, x: 100, y: -150 })
    expect(clampPan({ scale: 2, x: 40, y: 20 }, VIEWPORT, CONTENT)).toEqual({ scale: 2, x: 40, y: 20 })
  })

  it('centers an axis where the image is smaller than the viewport', () => {
    // 1.2× → 360px wide, still narrower than 400px.
    expect(clampPan({ scale: 1.2, x: 30, y: 10 }, VIEWPORT, CONTENT)).toEqual({ scale: 1.2, x: 0, y: 10 })
  })

  it('survives zero-size measurements', () => {
    expect(clampPan({ scale: 3, x: 50, y: -50 }, ZERO, ZERO)).toEqual({ scale: 3, x: 0, y: 0 })
  })
})

describe('zoomAt', () => {
  it('keeps the point under the cursor fixed', () => {
    const point = { x: 60, y: -40 }
    const next = zoomAt(IDENTITY, 2, point, VIEWPORT, CONTENT)
    expect(next).toEqual({ scale: 2, x: -60, y: 40 })
    // The image point that was under the cursor ((60, -40) at 1×) is still there.
    expect(next.x + 2 * 60).toBe(point.x)
    expect(next.y + 2 * -40).toBe(point.y)
  })

  it('zooms around the center when the point is the center', () => {
    expect(zoomAt(IDENTITY, 3, { x: 0, y: 0 }, VIEWPORT, CONTENT)).toEqual({ scale: 3, x: 0, y: 0 })
  })

  it('clamps the level and the pan', () => {
    const next = zoomAt(IDENTITY, 10, { x: 190, y: 140 }, VIEWPORT, CONTENT)
    expect(next.scale).toBe(MAX_SCALE)
    // 4× → 1200px: at most 400px of horizontal and 450px of vertical slack.
    expect(Math.abs(next.x)).toBeLessThanOrEqual(400)
    expect(Math.abs(next.y)).toBeLessThanOrEqual(450)
  })

  it('returns to the identity at 1×', () => {
    expect(zoomAt({ scale: 2, x: 50, y: 20 }, 1, { x: 10, y: 10 }, VIEWPORT, CONTENT)).toBe(IDENTITY)
    expect(zoomAt({ scale: 2, x: 50, y: 20 }, 0.5, { x: 10, y: 10 }, VIEWPORT, CONTENT)).toBe(IDENTITY)
  })

  it('survives zero-size measurements', () => {
    expect(zoomAt(IDENTITY, 2, { x: 0, y: 0 }, ZERO, ZERO)).toEqual({ scale: 2, x: 0, y: 0 })
  })
})

describe('toggleAt', () => {
  it('zooms a fitted image to 2.5× and a zoomed one back to fit', () => {
    const zoomed = toggleAt(IDENTITY, { x: 0, y: 0 }, VIEWPORT, CONTENT)
    expect(zoomed.scale).toBe(TOGGLE_SCALE)
    expect(toggleAt(zoomed, { x: 0, y: 0 }, VIEWPORT, CONTENT)).toBe(IDENTITY)
  })
})

describe('stepScale', () => {
  it('moves by half-steps within the limits', () => {
    expect(stepScale(1, 1)).toBe(1.5)
    expect(stepScale(2.5, 1)).toBe(3)
    expect(stepScale(4, 1)).toBe(4)
    expect(stepScale(1.5, -1)).toBe(1)
    expect(stepScale(1, -1)).toBe(1)
  })

  it('snaps in-between levels to the grid', () => {
    expect(stepScale(1.73, 1)).toBe(2)
    expect(stepScale(1.73, -1)).toBe(1.5)
  })
})

describe('formatZoom', () => {
  it('renders the level as a rounded percentage', () => {
    expect(formatZoom(1)).toBe('100%')
    expect(formatZoom(2.5)).toBe('250%')
    expect(formatZoom(1.234)).toBe('123%')
  })
})
