import type { Priority } from '../api/types'
import { PRIORITY_LABEL } from './format'
import styles from './PriorityBadge.module.css'

interface PriorityBadgeProps {
  priority: Priority
  /**
   * `confirmed` (default): solid tag – a clinician decided this.
   * `suggested`: hollow, dashed tag – advisory only, not applied.
   */
  variant?: 'confirmed' | 'suggested'
  size?: 'md' | 'lg'
}

/**
 * A triage tag. Priority is always text plus a shape (triangle, diamond, circle), never
 * colour alone, so it reads for colour-blind users and in greyscale print.
 */
export function PriorityBadge({ priority, variant = 'confirmed', size = 'md' }: PriorityBadgeProps) {
  return (
    <span
      className={styles.badge}
      data-priority={priority}
      data-variant={variant}
      data-size={size}
    >
      <PriorityShape priority={priority} hollow={variant === 'suggested'} />
      <span className={styles.label}>{PRIORITY_LABEL[priority]}</span>
      {variant === 'suggested' && <span className="visually-hidden"> (AI suggestion)</span>}
    </span>
  )
}

function PriorityShape({ priority, hollow }: { priority: Priority; hollow: boolean }) {
  const paint = hollow
    ? { fill: 'none', stroke: 'currentColor', strokeWidth: 1.6, strokeLinejoin: 'round' as const }
    : { fill: 'currentColor' }
  return (
    <svg className={styles.shape} viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      {priority === 'URGENT' && (
        <>
          <path d="M8 1.6 15.2 14.4H.8Z" {...paint} />
          <path d="M8 5.8v4" className={styles.cut} strokeWidth="1.9" strokeLinecap="round" />
          <circle cx="8" cy="12.2" r="1.05" className={styles.cutDot} />
        </>
      )}
      {priority === 'SOON' && <path d="M8 1.2 14.8 8 8 14.8 1.2 8Z" {...paint} />}
      {priority === 'ROUTINE' && <circle cx="8" cy="8" r="6" {...paint} />}
    </svg>
  )
}
