import { useId, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { isApiError } from '../../api/client'
import type { ImportResult } from '../../api/types'
import { ErrorBanner } from '../../components/ErrorBanner'
import { PageHeader } from '../../components/PageHeader'
import ui from '../../components/ui.module.css'
import { detectContentType, useImportReferral } from './hooks'
import { SAMPLE_JSON, SAMPLE_XML } from './samples'
import styles from './ImportPage.module.css'

/** US-1, US-2, US-3 (ADMIN): paste a GP message and import it. */
export function ImportPage() {
  const [message, setMessage] = useState('')
  const [missing, setMissing] = useState(false)
  const importReferral = useImportReferral()
  const hintId = useId()
  const errorId = useId()

  const format = detectContentType(message) === 'application/xml' ? 'XML' : 'JSON'

  function loadSample(sample: string) {
    setMessage(sample)
    setMissing(false)
    importReferral.reset()
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (message.trim() === '') {
      setMissing(true)
      return
    }
    setMissing(false)
    importReferral.mutate(message)
  }

  return (
    <>
      <PageHeader
        title="Import a referral"
        description="Paste one message from a GP system. JSON and XML are both accepted. Messages that fail validation are kept as dead letters."
      />

      <form className={styles.layout} onSubmit={handleSubmit} noValidate>
        <div className={styles.editor}>
          <div className={styles.editorBar}>
            <label className={ui.label} htmlFor="import-message">
              Referral message
            </label>
            <div className={ui.actions}>
              <button type="button" className={ui.secondary} onClick={() => loadSample(SAMPLE_JSON)}>
                Load sample JSON
              </button>
              <button type="button" className={ui.secondary} onClick={() => loadSample(SAMPLE_XML)}>
                Load sample XML
              </button>
            </div>
          </div>
          <textarea
            id="import-message"
            className={`${ui.textarea} ${styles.message}`}
            rows={14}
            spellCheck={false}
            value={message}
            onChange={(event) => {
              setMessage(event.target.value)
              setMissing(false)
            }}
            aria-invalid={missing || undefined}
            aria-describedby={missing ? `${errorId} ${hintId}` : hintId}
          />
          <p id={hintId} className={ui.hint}>
            {message.trim() === ''
              ? 'Text starting with < is sent as XML; anything else as JSON.'
              : `Will be sent as ${format}.`}
          </p>
          {missing && (
            <p id={errorId} className={ui.fieldError}>
              Paste a message or load a sample first.
            </p>
          )}
          <div className={ui.actions}>
            <button type="submit" className={ui.primary} disabled={importReferral.isPending}>
              {importReferral.isPending ? 'Importing…' : 'Import referral'}
            </button>
          </div>
        </div>

        <div className={styles.result} aria-live="polite">
          {importReferral.isSuccess && <ImportSuccess result={importReferral.data} />}
          {importReferral.isError && <ImportFailure error={importReferral.error} />}
        </div>
      </form>
    </>
  )
}

function ImportSuccess({ result }: { result: ImportResult }) {
  return result.created ? (
    <div className={styles.outcome} data-outcome="created">
      <h2>Referral imported</h2>
      <p>It is now in the referral inbox, waiting for triage.</p>
      <p>
        <Link to={`/referrals/${result.id}`}>Open referral {result.id}</Link>
      </p>
    </div>
  ) : (
    <div className={styles.outcome} data-outcome="duplicate">
      <h2>Already imported</h2>
      <p>A referral with this external reference already exists, so nothing was changed.</p>
      <p>
        <Link to={`/referrals/${result.id}`}>Open referral {result.id}</Link>
      </p>
    </div>
  )
}

function ImportFailure({ error }: { error: Error }) {
  if (!isApiError(error, 422)) {
    return <ErrorBanner error={error} title="The message was not imported" announce={false} />
  }
  return (
    <ErrorBanner error={error} announce={false}>
      {error.errors.length > 0 && (
        <ul className={styles.errorList}>
          {error.errors.map((fieldError) => (
            <li key={`${fieldError.field}:${fieldError.message}`}>
              <code>{fieldError.field}</code>: {fieldError.message}
            </li>
          ))}
        </ul>
      )}
      {error.deadLetterId !== undefined && (
        <p>
          Saved as dead letter {error.deadLetterId}.{' '}
          <Link to="/dead-letters">View dead letters</Link>
        </p>
      )}
    </ErrorBanner>
  )
}
