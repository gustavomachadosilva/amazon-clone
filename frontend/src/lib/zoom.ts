// Pure zoom/pan math for the fullscreen image viewer. The image is centered in a viewport and
// drawn with `transform: translate(x, y) scale(scale)` (origin at its center), so every point
// here is measured in px relative to the viewport center.

export const MIN_SCALE = 1
export const MAX_SCALE = 4
// Level a click/tap on a fitted image jumps to.
export const TOGGLE_SCALE = 2.5
// Increment used by the + / − buttons and keys.
export const ZOOM_STEP = 0.5

export interface ZoomState {
  scale: number
  x: number
  y: number
}

export interface Size {
  w: number
  h: number
}

export interface Point {
  x: number
  y: number
}

export const IDENTITY: ZoomState = { scale: 1, x: 0, y: 0 }

// Anything this close to 1× counts as "fit" (wheel/pinch multiply into float noise).
const EPSILON = 1e-3

export function clampScale(scale: number): number {
  if (!Number.isFinite(scale)) return MIN_SCALE
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale))
}

function clamp(value: number, limit: number): number {
  return Math.min(limit, Math.max(-limit, value))
}

// Keeps the scaled image covering the viewport on each axis where it is larger than it, and
// centered on each axis where it is smaller — so it can never be dragged off screen.
export function clampPan(state: ZoomState, viewport: Size, content: Size): ZoomState {
  const maxX = Math.max(0, (content.w * state.scale - viewport.w) / 2)
  const maxY = Math.max(0, (content.h * state.scale - viewport.h) / 2)
  // `+ 0` turns a clamped -0 into 0.
  return { scale: state.scale, x: clamp(state.x, maxX) + 0, y: clamp(state.y, maxY) + 0 }
}

// Zooms to `next` keeping the image point under `point` fixed on screen.
export function zoomAt(
  state: ZoomState,
  next: number,
  point: Point,
  viewport: Size,
  content: Size,
): ZoomState {
  const scale = clampScale(next)
  if (scale - MIN_SCALE < EPSILON) return IDENTITY
  const k = scale / state.scale
  return clampPan(
    { scale, x: point.x - (point.x - state.x) * k, y: point.y - (point.y - state.y) * k },
    viewport,
    content,
  )
}

// Click/tap: a fitted image zooms in on the clicked point; a zoomed one goes back to fit.
export function toggleAt(state: ZoomState, point: Point, viewport: Size, content: Size): ZoomState {
  if (state.scale > MIN_SCALE) return IDENTITY
  return zoomAt(state, TOGGLE_SCALE, point, viewport, content)
}

// Next level on the ZOOM_STEP grid in `direction` (1 = in, -1 = out), so an in-between level
// left by the wheel or a pinch snaps to the grid instead of drifting off it.
export function stepScale(scale: number, direction: 1 | -1): number {
  const steps = scale / ZOOM_STEP
  const next =
    direction > 0
      ? (Math.floor(steps + EPSILON) + 1) * ZOOM_STEP
      : (Math.ceil(steps - EPSILON) - 1) * ZOOM_STEP
  return clampScale(next)
}

export function formatZoom(scale: number): string {
  return `${Math.round(scale * 100)}%`
}
