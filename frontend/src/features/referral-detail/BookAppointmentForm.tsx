import { useId, useState, type FormEvent } from 'react'
import { isApiError } from '../../api/client'
import type { ReferralDetail } from '../../api/types'
import { ErrorBanner } from '../../components/ErrorBanner'
import {
  formatDateTime,
  localDateTimeToInstant,
  toDateTimeLocalValue,
} from '../../components/format'
import ui from '../../components/ui.module.css'
import { useBookAppointment } from './hooks'
import type { NoticeHandler } from './notice'
import styles from './ReferralDetailPage.module.css'

/** US-8 (ADMIN, TRIAGED only): book a single date and time. */
export function BookAppointmentForm({
  referral,
  onNotice,
}: {
  referral: ReferralDetail
  onNotice: NoticeHandler
}) {
  const [startsAt, setStartsAt] = useState('')
  const [missing, setMissing] = useState(false)
  // Earliest pickable time, fixed when the form opens (the server re-checks "future" anyway).
  const [earliest] = useState(() => toDateTimeLocalValue(new Date()))
  const errorId = useId()
  const hintId = useId()
  const book = useBookAppointment(referral.id, {
    onSuccess: (appointment) =>
      onNotice({
        tone: 'success',
        text: `Appointment booked for ${formatDateTime(appointment.startsAt)}.`,
      }),
    onError: (error) => {
      if (isApiError(error, 409)) {
        onNotice({
          tone: 'warning',
          text: 'This referral can no longer be booked. The latest version has been loaded.',
        })
      }
    },
  })

  const serverFieldError = isApiError(book.error, 400)
    ? (book.error.fieldError('startsAt') ?? book.error.detail)
    : undefined
  const error = missing ? 'Choose a date and time.' : serverFieldError
  const otherError =
    book.error && !isApiError(book.error, 400) && !isApiError(book.error, 409) ? book.error : null

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!startsAt) {
      setMissing(true)
      return
    }
    setMissing(false)
    onNotice(null)
    book.mutate({ startsAt: localDateTimeToInstant(startsAt) })
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <div className={ui.field}>
        <label className={ui.label} htmlFor={`starts-at-${referral.id}`}>
          Appointment date and time
        </label>
        <input
          id={`starts-at-${referral.id}`}
          type="datetime-local"
          className={`${ui.input} ${styles.dateInput}`}
          value={startsAt}
          min={earliest}
          onChange={(event) => {
            setStartsAt(event.target.value)
            setMissing(false)
          }}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${errorId} ${hintId}` : hintId}
        />
        <p id={hintId} className={ui.hint}>
          Your local time. Must be in the future.
        </p>
        {error && (
          <p id={errorId} className={ui.fieldError}>
            {error}
          </p>
        )}
      </div>
      {otherError && <ErrorBanner error={otherError} title="Could not book the appointment" />}
      <div className={ui.actions}>
        <button type="submit" className={ui.primary} disabled={book.isPending}>
          {book.isPending ? 'Booking…' : 'Book appointment'}
        </button>
      </div>
    </form>
  )
}
