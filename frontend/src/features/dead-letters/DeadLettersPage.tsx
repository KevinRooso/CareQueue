import { useId, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import type { DeadLetter } from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorBanner } from '../../components/ErrorBanner'
import { formatDateTime } from '../../components/format'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { parsePage, withPage } from '../../components/params'
import { Spinner } from '../../components/Spinner'
import table from '../../components/table.module.css'
import ui from '../../components/ui.module.css'
import { useDeadLetters } from './hooks'
import styles from './DeadLettersPage.module.css'

const PREVIEW_LENGTH = 120

/** US-3 (ADMIN): messages that failed to import, with the reason and the original payload. */
export function DeadLettersPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = parsePage(searchParams.get('page'))
  const deadLetters = useDeadLetters(page)

  let body
  if (deadLetters.isPending) {
    body = <Spinner label="Loading dead letters" />
  } else if (deadLetters.isError) {
    body = (
      <ErrorBanner error={deadLetters.error}>
        <p>
          <button type="button" className={ui.link} onClick={() => deadLetters.refetch()}>
            Try again
          </button>
        </p>
      </ErrorBanner>
    )
  } else if (deadLetters.data.page.totalElements === 0) {
    body = (
      <EmptyState title="No failed imports">
        Every message so far was imported. Messages that fail validation on the{' '}
        <Link to="/import">import page</Link> or from a GP system will appear here.
      </EmptyState>
    )
  } else {
    const { content, page: meta } = deadLetters.data
    body = (
      <>
        <div className={table.wrap}>
          <table
            className={`${table.table} ${deadLetters.isPlaceholderData ? table.updating : ''}`}
          >
            <caption className={table.caption}>
              <span className={table.captionStrong}>Failed imports</span>: {meta.totalElements}{' '}
              {meta.totalElements === 1 ? 'message' : 'messages'}, newest first
            </caption>
            <thead>
              <tr>
                <th scope="col">ID</th>
                <th scope="col">Received</th>
                <th scope="col">Format</th>
                <th scope="col">Error</th>
                <th scope="col">Payload</th>
              </tr>
            </thead>
            <tbody>
              {content.map((deadLetter) => (
                <DeadLetterRow key={deadLetter.id} deadLetter={deadLetter} />
              ))}
            </tbody>
          </table>
        </div>
        <Pagination
          page={meta.number}
          totalPages={meta.totalPages}
          onPageChange={(next) => setSearchParams(withPage(searchParams, next))}
          label="Dead letter pages"
        />
      </>
    )
  }

  return (
    <>
      <PageHeader
        title="Dead letters"
        description="Messages that failed to import. Each one keeps the original payload and the reason it was rejected, so nothing is lost."
      />
      {body}
    </>
  )
}

function DeadLetterRow({ deadLetter }: { deadLetter: DeadLetter }) {
  const [expanded, setExpanded] = useState(false)
  const payloadId = useId()
  const isLong = deadLetter.payload.length > PREVIEW_LENGTH
  const preview = isLong
    ? `${deadLetter.payload.slice(0, PREVIEW_LENGTH).trimEnd()}…`
    : deadLetter.payload

  return (
    <tr>
      <th scope="row" className={table.numeric}>
        {deadLetter.id}
      </th>
      <td className={table.numeric}>
        <time dateTime={deadLetter.receivedAt}>{formatDateTime(deadLetter.receivedAt)}</time>
      </td>
      <td>{deadLetter.source}</td>
      <td className={styles.error}>{deadLetter.error}</td>
      <td className={styles.payloadCell}>
        {/* Expanded payloads can scroll, so they must be reachable by keyboard. */}
        <pre
          id={payloadId}
          className={styles.payload}
          data-expanded={expanded}
          tabIndex={expanded ? 0 : undefined}
        >
          {expanded ? deadLetter.payload : preview}
        </pre>
        {isLong && (
          <button
            type="button"
            className={ui.link}
            aria-expanded={expanded}
            aria-controls={payloadId}
            onClick={() => setExpanded((value) => !value)}
          >
            {expanded ? 'Show less' : 'Show full payload'}
          </button>
        )}
      </td>
    </tr>
  )
}
