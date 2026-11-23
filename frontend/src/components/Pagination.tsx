import ui from './ui.module.css'
import styles from './Pagination.module.css'

interface PaginationProps {
  /** zero-based, as in the API */
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  /** Accessible name of the navigation landmark, e.g. "Waiting list pages". */
  label: string
}

export function Pagination({ page, totalPages, onPageChange, label }: PaginationProps) {
  if (totalPages <= 1) return null
  return (
    <nav className={styles.pagination} aria-label={label}>
      <button
        type="button"
        className={ui.secondary}
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        Previous page
      </button>
      <span className={styles.position} aria-live="polite">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className={ui.secondary}
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        Next page
      </button>
    </nav>
  )
}
