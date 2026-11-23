import type { ReactNode } from 'react'
import styles from './EmptyState.module.css'

/** An empty list is an invitation to act: say why it is empty and what to do next. */
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className={styles.empty}>
      <p className={styles.title}>{title}</p>
      {children && <div className={styles.body}>{children}</div>}
    </div>
  )
}
