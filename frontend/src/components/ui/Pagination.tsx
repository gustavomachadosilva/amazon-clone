export interface PaginationProps {
  currentPage: number // 0-indexed
  totalPages: number
  totalElements?: number
  pageSize?: number
  onPageChange: (page: number) => void
}

export default function Pagination({
  currentPage,
  totalPages,
  totalElements,
  pageSize = 10,
  onPageChange,
}: PaginationProps) {
  if (totalPages <= 1) return null

  // Generate page numbers array with ellipses if totalPages > 5
  const pages: (number | '...')[] = []
  const maxButtons = 5

  if (totalPages <= maxButtons) {
    for (let i = 0; i < totalPages; i++) pages.push(i)
  } else {
    pages.push(0)
    let start = Math.max(1, currentPage - 1)
    let end = Math.min(totalPages - 2, currentPage + 1)

    if (currentPage <= 2) {
      end = 3
    } else if (currentPage >= totalPages - 3) {
      start = totalPages - 4
    }

    if (start > 1) pages.push('...')
    for (let i = start; i <= end; i++) pages.push(i)
    if (end < totalPages - 2) pages.push('...')
    pages.push(totalPages - 1)
  }

  const startItem = currentPage * pageSize + 1
  const endItem = totalElements ? Math.min((currentPage + 1) * pageSize, totalElements) : (currentPage + 1) * pageSize

  return (
    <nav
      aria-label="Pagination"
      className="mt-6 flex flex-col items-center gap-3 border-t border-divider py-4 text-sm sm:flex-row sm:justify-between sm:gap-2"
    >
      <div className="text-[13px] text-[#5d5d60]">
        {totalElements !== undefined ? (
          <>
            Showing <strong>{startItem}</strong>–<strong>{endItem}</strong> of <strong>{totalElements}</strong> results
          </>
        ) : (
          <>
            Page <strong>{currentPage + 1}</strong> of <strong>{totalPages}</strong>
          </>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-center gap-1.5">
        <button
          className="btn btn-secondary min-h-11 px-3 text-[13px]"
          disabled={currentPage === 0}
          onClick={() => onPageChange(currentPage - 1)}
        >
          &laquo; Previous
        </button>

        {pages.map((p, idx) => {
          if (p === '...') {
            return (
              <span key={`ellipsis-${idx}`} className="px-1 text-[#98989b]">
                ...
              </span>
            )
          }

          const isCurrent = p === currentPage
          return (
            <button
              key={p}
              className={`btn min-h-11 min-w-11 px-2.5 text-[13px] ${isCurrent ? 'btn-primary font-semibold' : 'btn-secondary'}`}
              aria-current={isCurrent ? 'page' : undefined}
              onClick={() => onPageChange(p)}
            >
              {p + 1}
            </button>
          )
        })}

        <button
          className="btn btn-secondary min-h-11 px-3 text-[13px]"
          disabled={currentPage >= totalPages - 1}
          onClick={() => onPageChange(currentPage + 1)}
        >
          Next &raquo;
        </button>
      </div>
    </nav>
  )
}
