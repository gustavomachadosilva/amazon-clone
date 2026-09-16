import { Star } from 'lucide-react'

interface StarRatingProps {
  rating: number
  className?: string
  size?: number
}

export default function StarRating({ rating, className = '', size = 18 }: StarRatingProps) {
  const rounded = Math.round(rating)
  return (
    <span
      className={`inline-flex items-center gap-[1px] text-accent-700 ${className}`}
      role="img"
      aria-label={`${rating.toFixed(1)} out of 5 stars`}
    >
      {Array.from({ length: 5 }, (_, i) => (
        <Star key={i} size={size} strokeWidth={1.5} fill={i < rounded ? 'currentColor' : 'none'} />
      ))}
    </span>
  )
}
