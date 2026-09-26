import { useState } from 'react'
import { ImageZoomViewer, Placeholder } from './ui'

interface ProductMainImageProps {
  name: string
  imageUrl?: string
}

// Main photo on the product page. With a real image it opens the fullscreen zoom viewer; the
// striped placeholder shown for products without one stays static.
export default function ProductMainImage({ name, imageUrl }: ProductMainImageProps) {
  const [viewerOpen, setViewerOpen] = useState(false)

  if (!imageUrl) return <Placeholder label="Main photo" aspect="1/1" priority />

  return (
    <>
      <button
        type="button"
        aria-label={`View larger image of ${name}`}
        className="block w-full cursor-zoom-in"
        onClick={(event) => {
          // Safari doesn't focus buttons on click; the viewer returns focus to whatever had it.
          event.currentTarget.focus()
          setViewerOpen(true)
        }}
      >
        <Placeholder label="Main photo" aspect="1/1" src={imageUrl} priority />
      </button>
      {viewerOpen && (
        <ImageZoomViewer
          src={imageUrl}
          alt={name}
          label={`Photo of ${name}`}
          onClose={() => setViewerOpen(false)}
        />
      )}
    </>
  )
}
