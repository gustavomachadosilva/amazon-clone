interface PlaceholderProps {
  label: string
  duotone?: boolean
  aspect?: string
  className?: string
  src?: string
  priority?: boolean
}

export default function Placeholder({
  label,
  duotone = true,
  aspect = '1/1',
  className = '',
  src,
  priority = false,
}: PlaceholderProps) {
  if (src) {
    return (
      <img
        src={src}
        alt={label}
        loading={priority ? 'eager' : 'lazy'}
        decoding="async"
        className={`ph-image ${className}`}
        style={{ aspectRatio: aspect }}
      />
    )
  }

  return (
    <div
      className={`ph ${duotone ? 'duotone' : ''} ${className}`}
      style={{ aspectRatio: aspect }}
    >
      <span>{label}</span>
    </div>
  )
}
