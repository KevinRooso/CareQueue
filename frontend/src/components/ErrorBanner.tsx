import type { ReactNode } from 'react'
import { isApiError } from '../api/client'
import styles from './ErrorBanner.module.css'

interface ErrorBannerProps {
  error?: unknown
  /** Overrides the title taken from the error. */
  title?: string
  /**
   * `role="alert"` makes screen readers read the banner out at once. Turn it off when the
   * banner is already inside an `aria-live` region, so it is not announced twice.
   */
  announce?: boolean
  children?: ReactNode
}

/** A failed request, explained: what went wrong and (via children) what to do next. */
export function ErrorBanner({ error, title, announce = true, children }: ErrorBannerProps) {
  const heading = title ?? describeTitle(error)
  const detail = describeDetail(error)
  return (
    <div className={styles.banner} role={announce ? 'alert' : undefined}>
      <svg className={styles.icon} viewBox="0 0 16 16" aria-hidden="true" focusable="false">
        <circle cx="8" cy="8" r="7.2" fill="currentColor" />
        <path d="M8 4.2v4.6" stroke="#fff" strokeWidth="1.9" strokeLinecap="round" />
        <circle cx="8" cy="11.5" r="1.05" fill="#fff" />
      </svg>
      <div className={styles.body}>
        <p className={styles.title}>{heading}</p>
        {detail && <p>{detail}</p>}
        {children}
      </div>
    </div>
  )
}

function describeTitle(error: unknown): string {
  if (isApiError(error, 403)) return 'Your role does not allow this'
  if (isApiError(error)) return error.title
  return 'Something went wrong'
}

function describeDetail(error: unknown): string | undefined {
  if (isApiError(error)) return error.detail || undefined
  if (error instanceof Error) return error.message
  return undefined
}
