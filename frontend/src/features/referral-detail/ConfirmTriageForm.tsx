import { useId, useState, type FormEvent } from 'react'
import { isApiError } from '../../api/client'
import { PRIORITIES, type Priority, type ReferralDetail } from '../../api/types'
import { ErrorBanner } from '../../components/ErrorBanner'
import { PRIORITY_LABEL, PRIORITY_MEANING } from '../../components/format'
import { PriorityBadge } from '../../components/PriorityBadge'
import ui from '../../components/ui.module.css'
import { CONFLICT_MESSAGE, useConfirmTriage } from './hooks'
import type { NoticeHandler } from './notice'
import styles from './ReferralDetailPage.module.css'

export const NOTE_MAX_LENGTH = 500

interface ConfirmTriageFormProps {
  referral: ReferralDetail
  onNotice: NoticeHandler
}

/**
 * US-5: the clinician's decision. The AI suggestion is preselected but nothing is sent until
 * the clinician presses the button. The request carries the `version` that was on screen, so
 * if someone else changed the referral meanwhile the server answers 409 (US-6).
 *
 * The parent renders this with `key={referral.version}`: when a new version arrives the form
 * starts fresh from it, with no effect needed to sync state.
 */
export function ConfirmTriageForm({ referral, onNotice }: ConfirmTriageFormProps) {
  const confirm = useConfirmTriage(referral.id, {
    onSuccess: (detail) => {
      const confirmed = detail.confirmedPriority
      onNotice({
        tone: 'success',
        text: confirmed
          ? `Priority confirmed: ${PRIORITY_LABEL[confirmed]}. The referral is now on the ${detail.clinic.name} waiting list.`
          : 'Priority confirmed.',
      })
    },
    onError: (error) => {
      if (isApiError(error, 409)) onNotice({ tone: 'warning', text: CONFLICT_MESSAGE })
    },
  })
  const [priority, setPriority] = useState<Priority | null>(referral.suggestedPriority)
  const [note, setNote] = useState('')
  const [missingPriority, setMissingPriority] = useState(false)
  const noteHintId = useId()
  const noteErrorId = useId()
  const priorityErrorId = useId()

  const serverError = confirm.error
  const fieldError = (field: string) =>
    isApiError(serverError, 400) ? serverError.fieldError(field) : undefined
  const priorityError = missingPriority ? 'Choose a priority.' : fieldError('priority')
  const noteError = fieldError('note')
  const otherError =
    serverError && !isApiError(serverError, 409) && !priorityError && !noteError
      ? serverError
      : null

  const suggested = referral.suggestedPriority
  const changingSuggestion = suggested !== null && priority !== null && priority !== suggested

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!priority) {
      setMissingPriority(true)
      return
    }
    setMissingPriority(false)
    onNotice(null)
    const trimmed = note.trim()
    confirm.mutate({ priority, note: trimmed === '' ? null : trimmed, version: referral.version })
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <fieldset
        className={styles.priorityGroup}
        aria-describedby={priorityError ? priorityErrorId : undefined}
      >
        <legend className={ui.label}>Priority</legend>
        {PRIORITIES.map((option) => (
          <label key={option} className={styles.priorityOption}>
            <input
              type="radio"
              name="priority"
              value={option}
              checked={priority === option}
              onChange={() => {
                setPriority(option)
                setMissingPriority(false)
              }}
            />
            <PriorityBadge priority={option} />
            <span className={styles.meaning}>{PRIORITY_MEANING[option]}</span>
            {option === suggested && <span className={styles.suggestedMark}>AI suggestion</span>}
          </label>
        ))}
      </fieldset>
      {priorityError && (
        <p id={priorityErrorId} className={ui.fieldError}>
          {priorityError}
        </p>
      )}
      {changingSuggestion && (
        <p className={styles.changeHint}>
          You are changing the AI suggestion from {PRIORITY_LABEL[suggested]} to{' '}
          {PRIORITY_LABEL[priority]}. Add a note to record why.
        </p>
      )}

      <div className={ui.field}>
        <label className={ui.label} htmlFor={`note-${referral.id}`}>
          Note (optional)
        </label>
        <textarea
          id={`note-${referral.id}`}
          className={ui.textarea}
          rows={3}
          maxLength={NOTE_MAX_LENGTH}
          value={note}
          onChange={(event) => setNote(event.target.value)}
          aria-invalid={noteError ? true : undefined}
          aria-describedby={noteError ? `${noteErrorId} ${noteHintId}` : noteHintId}
        />
        <p id={noteHintId} className={ui.hint}>
          {note.length} of {NOTE_MAX_LENGTH} characters
        </p>
        {noteError && (
          <p id={noteErrorId} className={ui.fieldError}>
            {noteError}
          </p>
        )}
      </div>

      {otherError && <ErrorBanner error={otherError} title="Could not confirm the priority" />}

      <div className={ui.actions}>
        <button type="submit" className={ui.primary} disabled={confirm.isPending}>
          {confirm.isPending ? 'Confirming…' : 'Confirm priority'}
        </button>
      </div>
    </form>
  )
}
