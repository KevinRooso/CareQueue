import type { ReferralStatus } from '../api/types'
import { STATUS_LABEL } from './format'
import styles from './StatusBadge.module.css'

/** Referral status as text with a quiet shape; status never borrows the priority colours. */
export function StatusBadge({ status }: { status: ReferralStatus }) {
  return (
    <span className={styles.status} data-status={status}>
      <StatusGlyph status={status} />
      {STATUS_LABEL[status]}
    </span>
  )
}

function StatusGlyph({ status }: { status: ReferralStatus }) {
  return (
    <svg className={styles.glyph} viewBox="0 0 12 12" aria-hidden="true" focusable="false">
      {status === 'RECEIVED' && (
        <circle cx="6" cy="6" r="4.2" fill="none" stroke="currentColor" strokeWidth="1.5" />
      )}
      {status === 'AI_SUGGESTED' && (
        <circle
          cx="6"
          cy="6"
          r="4.2"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeDasharray="2.2 1.6"
        />
      )}
      {status === 'TRIAGED' && (
        <path
          d="M2.2 6.3 4.9 8.9 9.8 3.3"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      )}
      {status === 'BOOKED' && (
        <>
          <rect x="1.5" y="2.5" width="9" height="8" rx="1" fill="currentColor" />
          <path d="M1.5 5h9" stroke="var(--sheet)" strokeWidth="1.2" />
        </>
      )}
      {status === 'REJECTED' && (
        <path
          d="M3 3l6 6M9 3 3 9"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
        />
      )}
    </svg>
  )
}
