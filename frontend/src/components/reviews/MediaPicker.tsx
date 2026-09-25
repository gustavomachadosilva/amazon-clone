import { useEffect, useId, useRef, useState, type DragEvent } from 'react'
import { flushSync } from 'react-dom'
import { Play, X } from 'lucide-react'
import { Button } from '../ui'
import {
  MAX_REVIEW_MEDIA_FILES,
  REVIEW_MEDIA_ACCEPT,
  validateReviewMediaFile,
  type MediaKind,
} from '../../lib/reviewMedia'

interface PickedMedia {
  id: number
  file: File
  kind: MediaKind
  url: string
}

interface Rejection {
  key: string
  name: string
  reason: string
}

export interface MediaPickerProps {
  onChange: (files: File[]) => void
  disabled?: boolean
  id?: string
}

const LIMIT_REASON = `Limit reached — a review can have at most ${MAX_REVIEW_MEDIA_FILES} photos or videos`

/**
 * Photo/video picker for the review form: click or drop files on the ledger-paper area, preview
 * them as thumbnails and remove them. Files are validated with the backend's rules; a refused
 * file gets an inline message and never discards the ones already accepted.
 */
export default function MediaPicker({ onChange, disabled = false, id }: MediaPickerProps) {
  const [items, setItems] = useState<PickedMedia[]>([])
  const [rejections, setRejections] = useState<Rejection[]>([])
  const [dragOver, setDragOver] = useState(false)

  const inputRef = useRef<HTMLInputElement>(null)
  const addButtonRef = useRef<HTMLButtonElement>(null)
  const listRef = useRef<HTMLUListElement>(null)
  const nextIdRef = useRef(0)
  // Mirror of `items`, written only in handlers, so the unmount cleanup can revoke what's left.
  const itemsRef = useRef<PickedMedia[]>([])

  const hintId = useId()
  const full = items.length >= MAX_REVIEW_MEDIA_FILES

  useEffect(() => () => itemsRef.current.forEach((item) => URL.revokeObjectURL(item.url)), [])

  function commit(next: PickedMedia[]) {
    itemsRef.current = next
    onChange(next.map((item) => item.file))
  }

  function addFiles(list: FileList) {
    const next = [...itemsRef.current]
    const refused: Rejection[] = []
    for (const file of Array.from(list)) {
      const key = `${nextIdRef.current++}`
      const result = validateReviewMediaFile(file)
      if (!result.ok) {
        refused.push({ key, name: file.name, reason: result.reason })
      } else if (next.length >= MAX_REVIEW_MEDIA_FILES) {
        refused.push({ key, name: file.name, reason: LIMIT_REASON })
      } else {
        next.push({ id: nextIdRef.current++, file, kind: result.kind, url: URL.createObjectURL(file) })
      }
    }
    setRejections(refused)
    setItems(next)
    commit(next)
  }

  function remove(id: number) {
    const current = itemsRef.current
    const index = current.findIndex((item) => item.id === id)
    if (index === -1) return
    URL.revokeObjectURL(current[index].url)
    const next = current.filter((item) => item.id !== id)
    // Render synchronously so the add button is re-enabled before focus moves onto it.
    flushSync(() => setItems(next))
    commit(next)
    const buttons = listRef.current?.querySelectorAll<HTMLButtonElement>('button[data-remove]')
    const target = buttons?.[index] ?? buttons?.[index - 1] ?? addButtonRef.current
    target?.focus()
  }

  function onDragEnter(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    if (!disabled) setDragOver(true)
  }

  function onDragLeave(e: DragEvent<HTMLDivElement>) {
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragOver(false)
  }

  function onDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setDragOver(false)
    if (!disabled && e.dataTransfer.files.length > 0) addFiles(e.dataTransfer.files)
  }

  return (
    <div className="flex flex-col gap-3">
      <div
        id={id}
        data-testid="media-drop-zone"
        onDragEnter={onDragEnter}
        onDragOver={onDragEnter}
        onDragLeave={onDragLeave}
        onDrop={onDrop}
        className={`ph min-h-24 flex-col gap-3 p-4 text-center ${dragOver ? 'ph-dragover' : ''}`}
      >
        <span>Add a photo or video</span>
        <Button
          ref={addButtonRef}
          type="button"
          variant="secondary"
          onClick={() => inputRef.current?.click()}
          disabled={disabled || full}
          aria-describedby={hintId}
        >
          Add photos or videos
        </Button>
        <input
          ref={inputRef}
          type="file"
          multiple
          accept={REVIEW_MEDIA_ACCEPT}
          className="hidden"
          tabIndex={-1}
          aria-hidden="true"
          data-testid="media-input"
          onChange={(e) => {
            if (e.target.files) addFiles(e.target.files)
            e.target.value = ''
          }}
        />
        <p id={hintId} className="m-0 text-sm text-paper-700">
          JPEG, PNG or WebP up to 5 MB · MP4 or WebM up to 50 MB · max {MAX_REVIEW_MEDIA_FILES} files
        </p>
      </div>

      <p aria-live="polite" className="m-0 font-mono text-sm text-paper-700">
        {items.length} of {MAX_REVIEW_MEDIA_FILES}
      </p>

      {items.length > 0 && (
        <ul
          ref={listRef}
          aria-label="Selected photos and videos"
          className="m-0 grid list-none grid-cols-3 gap-2 p-0 sm:grid-cols-5"
        >
          {items.map((item) => (
            <li key={item.id} className="relative aspect-square overflow-hidden border border-divider bg-surface">
              {item.kind === 'image' ? (
                <img src={item.url} alt={item.file.name} className="h-full w-full object-cover" />
              ) : (
                <>
                  <video
                    src={`${item.url}#t=0.1`}
                    muted
                    playsInline
                    preload="metadata"
                    aria-label={item.file.name}
                    className="h-full w-full object-cover"
                  />
                  <span className="pointer-events-none absolute inset-0 flex items-center justify-center text-white">
                    <Play aria-hidden size={28} strokeWidth={1.5} fill="currentColor" className="drop-shadow" />
                  </span>
                </>
              )}
              <button
                type="button"
                data-remove
                onClick={() => remove(item.id)}
                disabled={disabled}
                aria-label={`Remove ${item.file.name}`}
                className="absolute right-0 top-0 flex h-11 w-11 items-center justify-center border-0 bg-paper-900/70 text-white disabled:opacity-50"
              >
                <X aria-hidden size={20} />
              </button>
            </li>
          ))}
        </ul>
      )}

      {rejections.length > 0 && (
        <ul role="alert" className="m-0 flex list-none flex-col gap-1 break-all p-0 text-sm text-alert-700">
          {rejections.map((r) => (
            <li key={r.key}>
              <strong>{r.name}</strong>: {r.reason}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
