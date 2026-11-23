import styles from './Spinner.module.css'

/** Loading indicator. The ring stops spinning under prefers-reduced-motion; the text stays. */
export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className={styles.spinner} role="status">
      <span className={styles.ring} aria-hidden="true" />
      <span>{label}…</span>
    </div>
  )
}
