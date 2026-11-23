import type { ReactNode } from 'react'
import styles from './PageHeader.module.css'

interface PageHeaderProps {
  title: string
  description?: ReactNode
  /** Controls shown at the right (e.g. a clinic selector). */
  actions?: ReactNode
  /** Small context line above the title, e.g. a back link. */
  context?: ReactNode
}

export function PageHeader({ title, description, actions, context }: PageHeaderProps) {
  return (
    <header className={styles.header}>
      {/* React 19 hoists <title> into <head>. */}
      <title>{`${title} – CareQueue`}</title>
      <div className={styles.text}>
        {context && <div className={styles.context}>{context}</div>}
        <h1>{title}</h1>
        {description && <p className={styles.description}>{description}</p>}
      </div>
      {actions && <div className={styles.actions}>{actions}</div>}
    </header>
  )
}
