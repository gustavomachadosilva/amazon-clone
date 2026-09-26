import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent, type PointerEvent } from 'react'
import { RotateCcw, ZoomIn, ZoomOut } from 'lucide-react'
import Lightbox from './Lightbox'
import { LIGHTBOX_CONTROL_CLASS } from './lightboxStyles'
import {
  IDENTITY,
  MAX_SCALE,
  MIN_SCALE,
  clampPan,
  clampScale,
  formatZoom,
  stepScale,
  toggleAt,
  zoomAt,
  type Point,
  type Size,
  type ZoomState,
} from '../../lib/zoom'

interface ImageZoomViewerProps {
  src: string
  alt: string
  // Accessible name of the dialog, e.g. "Photo of Wireless Headphones".
  label: string
  onClose: () => void
}

// A pointer that travels further than this between down and up is a drag, not a click.
const CLICK_SLOP_PX = 5
// Wheel delta → zoom factor; exponential so zooming in and back out lands on the same level.
const WHEEL_SENSITIVITY = 0.0015

type Gesture =
  | { kind: 'pan'; start: Point; origin: ZoomState }
  | { kind: 'pinch'; distance: number; mid: Point; origin: ZoomState }

function distance(a: Point, b: Point): number {
  return Math.hypot(a.x - b.x, a.y - b.y)
}

function midpoint(a: Point, b: Point): Point {
  return { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }
}

// Sizes and the viewport center, read from the DOM only inside handlers and effects. The image
// size is its layout size (transforms don't affect offsetWidth/Height).
function measure(
  viewportEl: HTMLElement | null,
  imageEl: HTMLElement | null,
): { viewport: Size; content: Size; center: Point } {
  const rect = viewportEl?.getBoundingClientRect()
  return {
    viewport: { w: viewportEl?.clientWidth ?? 0, h: viewportEl?.clientHeight ?? 0 },
    content: { w: imageEl?.offsetWidth ?? 0, h: imageEl?.offsetHeight ?? 0 },
    center: rect ? { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 } : { x: 0, y: 0 },
  }
}

function relativeToCenter(clientX: number, clientY: number, center: Point): Point {
  return { x: clientX - center.x, y: clientY - center.y }
}

// Fullscreen photo viewer: click/tap toggles fit ↔ 2.5× on the clicked point, + / − / reset
// buttons and keys, wheel and pinch zoom, drag to pan while zoomed.
export default function ImageZoomViewer({ src, alt, label, onClose }: ImageZoomViewerProps) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const imageRef = useRef<HTMLImageElement>(null)
  const [zoom, setZoom] = useState<ZoomState>(IDENTITY)
  // True while a finger/mouse drag or pinch is in progress (no transition, grabbing cursor).
  const [gesturing, setGesturing] = useState(false)
  const pointers = useRef(new Map<number, Point>())
  const gesture = useRef<Gesture | null>(null)
  // Set once the current press moved past the slop, so its trailing click doesn't toggle zoom.
  const moved = useRef(false)

  // Wheel zoom around the cursor. Registered natively because React's wheel listener is passive,
  // and the page (or the browser's own zoom) must not scroll underneath.
  useEffect(() => {
    const viewportEl = viewportRef.current
    if (!viewportEl) return
    function handleWheel(event: WheelEvent) {
      event.preventDefault()
      const { viewport, content, center } = measure(viewportRef.current, imageRef.current)
      const point = relativeToCenter(event.clientX, event.clientY, center)
      const factor = Math.exp(-event.deltaY * WHEEL_SENSITIVITY)
      setZoom((prev) => zoomAt(prev, clampScale(prev.scale * factor), point, viewport, content))
    }
    viewportEl.addEventListener('wheel', handleWheel, { passive: false })
    return () => viewportEl.removeEventListener('wheel', handleWheel)
  }, [])

  // Rotating a phone or resizing the window shrinks the room to pan in: re-clamp.
  useEffect(() => {
    const viewportEl = viewportRef.current
    if (!viewportEl || typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(() => {
      const { viewport, content } = measure(viewportRef.current, imageRef.current)
      setZoom((prev) => {
        const next = clampPan(prev, viewport, content)
        return next.x === prev.x && next.y === prev.y ? prev : next
      })
    })
    observer.observe(viewportEl)
    return () => observer.disconnect()
  }, [])

  function zoomAroundCenter(next: number) {
    const { viewport, content } = measure(viewportRef.current, imageRef.current)
    setZoom((prev) => zoomAt(prev, next, { x: 0, y: 0 }, viewport, content))
  }

  const canZoomIn = zoom.scale < MAX_SCALE
  const canZoomOut = zoom.scale > MIN_SCALE

  function zoomIn() {
    if (canZoomIn) zoomAroundCenter(stepScale(zoom.scale, 1))
  }

  function zoomOut() {
    if (canZoomOut) zoomAroundCenter(stepScale(zoom.scale, -1))
  }

  function resetZoom() {
    setZoom(IDENTITY)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDialogElement>) {
    // Ctrl/Cmd + and − belong to the browser's page zoom.
    if (event.ctrlKey || event.metaKey || event.altKey) return
    if (event.key === '+' || event.key === '=') {
      event.preventDefault()
      zoomIn()
    } else if (event.key === '-' || event.key === '_') {
      event.preventDefault()
      zoomOut()
    } else if (event.key === '0') {
      event.preventDefault()
      resetZoom()
    }
  }

  function startPinch(origin: ZoomState) {
    const [a, b] = [...pointers.current.values()]
    gesture.current = { kind: 'pinch', distance: distance(a, b), mid: midpoint(a, b), origin }
    moved.current = true
  }

  function handlePointerDown(event: PointerEvent<HTMLDivElement>) {
    if (event.pointerType === 'mouse' && event.button !== 0) return
    pointers.current.set(event.pointerId, { x: event.clientX, y: event.clientY })
    // Keep receiving moves when the pointer leaves the viewport mid-drag (not in jsdom).
    event.currentTarget.setPointerCapture?.(event.pointerId)
    if (pointers.current.size === 1) {
      moved.current = false
      gesture.current = { kind: 'pan', start: { x: event.clientX, y: event.clientY }, origin: zoom }
    } else if (pointers.current.size === 2) {
      startPinch(zoom)
    }
    setGesturing(true)
  }

  function handlePointerMove(event: PointerEvent<HTMLDivElement>) {
    if (!pointers.current.has(event.pointerId)) return
    pointers.current.set(event.pointerId, { x: event.clientX, y: event.clientY })
    const current = gesture.current
    if (!current) return
    const { viewport, content, center } = measure(viewportRef.current, imageRef.current)

    if (current.kind === 'pinch') {
      const [a, b] = [...pointers.current.values()]
      if (!a || !b || current.distance === 0) return
      const mid = midpoint(a, b)
      const next = clampScale((current.origin.scale * distance(a, b)) / current.distance)
      // Zoom around where the pinch started, then follow the fingers as they move together.
      const zoomed = zoomAt(current.origin, next, relativeToCenter(current.mid.x, current.mid.y, center), viewport, content)
      setZoom(clampPan({ ...zoomed, x: zoomed.x + mid.x - current.mid.x, y: zoomed.y + mid.y - current.mid.y }, viewport, content))
      return
    }

    const dx = event.clientX - current.start.x
    const dy = event.clientY - current.start.y
    if (!moved.current && Math.hypot(dx, dy) < CLICK_SLOP_PX) return
    moved.current = true
    if (current.origin.scale > MIN_SCALE) {
      setZoom(clampPan({ ...current.origin, x: current.origin.x + dx, y: current.origin.y + dy }, viewport, content))
    }
  }

  function handlePointerEnd(event: PointerEvent<HTMLDivElement>) {
    if (!pointers.current.delete(event.pointerId)) return
    // Lifting one finger of a pinch ends the gesture; the other one doesn't start a pan.
    gesture.current = null
    if (pointers.current.size === 0) setGesturing(false)
  }

  function handleClick(event: MouseEvent<HTMLDivElement>) {
    if (moved.current) {
      moved.current = false
      return
    }
    const { viewport, content, center } = measure(viewportRef.current, imageRef.current)
    const point = relativeToCenter(event.clientX, event.clientY, center)
    setZoom((prev) => toggleAt(prev, point, viewport, content))
  }

  const zoomed = zoom.scale > MIN_SCALE
  const cursor = zoomed ? (gesturing ? 'cursor-grabbing' : 'cursor-grab') : 'cursor-zoom-in'

  return (
    <Lightbox label={label} onClose={onClose} onKeyDown={handleKeyDown}>
      {/* Not a backdrop: clicks here zoom, and a drag that ends here must not close. */}
      <div
        ref={viewportRef}
        className={`relative min-h-0 w-full flex-1 touch-none select-none overflow-hidden ${cursor}`}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerEnd}
        onPointerCancel={handlePointerEnd}
        onClick={handleClick}
      >
        <img
          ref={imageRef}
          src={src}
          alt={alt}
          draggable={false}
          className={`absolute inset-0 m-auto max-h-full max-w-full object-contain will-change-transform ${
            gesturing ? '' : 'transition-transform duration-200 ease-out motion-reduce:transition-none'
          }`}
          style={{ transform: `translate3d(${zoom.x}px, ${zoom.y}px, 0) scale(${zoom.scale})` }}
        />
      </div>

      <div className="flex items-center gap-4">
        <button
          type="button"
          aria-label="Zoom out"
          aria-disabled={!canZoomOut}
          className={LIGHTBOX_CONTROL_CLASS}
          onClick={zoomOut}
        >
          <ZoomOut size={22} strokeWidth={1.5} />
        </button>
        <p aria-live="polite" className="readout min-w-[4.5rem] text-center text-[15px] text-white">
          {formatZoom(zoom.scale)}
        </p>
        <button
          type="button"
          aria-label="Zoom in"
          aria-disabled={!canZoomIn}
          className={LIGHTBOX_CONTROL_CLASS}
          onClick={zoomIn}
        >
          <ZoomIn size={22} strokeWidth={1.5} />
        </button>
        <button
          type="button"
          aria-label="Reset zoom"
          aria-disabled={!zoomed}
          className={LIGHTBOX_CONTROL_CLASS}
          onClick={resetZoom}
        >
          <RotateCcw size={20} strokeWidth={1.5} />
        </button>
      </div>
    </Lightbox>
  )
}
