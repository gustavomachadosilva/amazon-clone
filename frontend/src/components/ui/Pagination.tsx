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
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '16px 0',
        marginTop: '24px',
        borderTop: '1px solid var(--color-divider, #e5e5e5)',
        fontSize: '14px',
      }}
    >
      <div style={{ color: '#5d5d60', fontSize: '13px' }}>
        {totalElements !== undefined ? (
          <>
            Exibindo <strong>{startItem}</strong>–<strong>{endItem}</strong> de <strong>{totalElements}</strong> resultados
          </>
        ) : (
          <>
            Página <strong>{currentPage + 1}</strong> de <strong>{totalPages}</strong>
          </>
        )}
      </div>

      <div style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
        <button
          className="btn btn-secondary"
          style={{ padding: '6px 12px', fontSize: '13px' }}
          disabled={currentPage === 0}
          onClick={() => onPageChange(currentPage - 1)}
        >
          &laquo; Anterior
        </button>

        {pages.map((p, idx) => {
          if (p === '...') {
            return (
              <span key={`ellipsis-${idx}`} style={{ padding: '0 4px', color: '#98989b' }}>
                ...
              </span>
            )
          }

          const isCurrent = p === currentPage
          return (
            <button
              key={p}
              className={isCurrent ? 'btn btn-primary' : 'btn btn-secondary'}
              style={{
                minWidth: '34px',
                padding: '6px 10px',
                fontSize: '13px',
                fontWeight: isCurrent ? 600 : 400,
              }}
              onClick={() => onPageChange(p)}
            >
              {p + 1}
            </button>
          )
        })}

        <button
          className="btn btn-secondary"
          style={{ padding: '6px 12px', fontSize: '13px' }}
          disabled={currentPage >= totalPages - 1}
          onClick={() => onPageChange(currentPage + 1)}
        >
          Próxima &raquo;
        </button>
      </div>
    </div>
  )
}
