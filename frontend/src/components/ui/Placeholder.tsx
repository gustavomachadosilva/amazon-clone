interface PlaceholderProps {
  label: string
  duotone?: boolean
  aspect?: string
  className?: string
  src?: string
}

export default function Placeholder({ label, duotone = true, aspect = '1/1', className = '', src }: PlaceholderProps) {
  if (src) {
    return (
      <img
        src={src}
        alt={label}
        className={`ph-image ${className}`}
        style={{ aspectRatio: aspect, objectFit: 'contain', width: '100%', height: '100%' }}
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
