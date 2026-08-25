interface StarRatingProps {
  rating: number
  className?: string
}

export default function StarRating({ rating, className = '' }: StarRatingProps) {
  const rounded = Math.round(rating)
  const stars = '★★★★★'.slice(0, rounded) + '☆☆☆☆☆'.slice(0, 5 - rounded)
  return (
    <span className={`stars ${className}`} role="img" aria-label={`${rating.toFixed(1)} out of 5 stars`}>
      {stars}
    </span>
  )
}
