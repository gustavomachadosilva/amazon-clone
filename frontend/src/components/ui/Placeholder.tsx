interface PlaceholderProps {
  label: string
  duotone?: boolean
  aspect?: string
  className?: string
}

export default function Placeholder({ label, duotone = true, aspect = '1/1', className = '' }: PlaceholderProps) {
  return (
    <div
      className={`ph ${duotone ? 'duotone' : ''} ${className}`}
      style={{ aspectRatio: aspect }}
    >
      <span>{label}</span>
    </div>
  )
}
