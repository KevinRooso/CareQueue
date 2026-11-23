import type { ReferralRevision } from '../../api/types'
import { formatDateTime } from '../../components/format'
import { useReferralHistory } from './hooks'
import styles from './ReferralDetailPage.module.css'

/** API field names as the clinician reads them. */
const FIELD_LABELS: Record<string, string> = {
  status: 'Status',
  suggestedPriority: 'Suggested priority',
  suggestionSource: 'Suggestion source',
  suggestionConfidence: 'Confidence',
  promptVersion: 'Prompt version',
  aiSummary: 'AI summary',
  redFlags: 'Red flags',
  confirmedPriority: 'Confirmed priority',
  triageNote: 'Triage note',
  triagedBy: 'Triaged by',
  triagedAt: 'Triaged at',
}

function RevisionRow({ revision }: { revision: ReferralRevision }) {
  const action =
    revision.type === 'CREATED' ? 'created the referral' : revision.type === 'DELETED' ? 'deleted the referral' : 'changed'
  return (
    <li className={styles.revision}>
      <p className={styles.revisionHead}>
        <strong>{revision.username}</strong> {action} ·{' '}
        <time dateTime={revision.timestamp}>{formatDateTime(revision.timestamp)}</time>
      </p>
      <ul className={styles.changeList}>
        {revision.changes.map((change) => (
          <li key={change.field}>
            <span className={styles.changeField}>{FIELD_LABELS[change.field] ?? change.field}</span>
            : {change.from ?? '—'} → {change.to ?? '—'}
          </li>
        ))}
      </ul>
    </li>
  )
}

/**
 * US-9, ADMIN only: every recorded revision of this referral — who, when and
 * which fields moved. The server keeps this behind `hasRole('ADMIN')`; the page
 * only mounts it for managers.
 */
export function HistoryPanel({ referralId }: { referralId: number }) {
  const { data: revisions, isPending, isError } = useReferralHistory(referralId)

  return (
    <section className={styles.panelQuiet} aria-labelledby="history-heading">
      <h2 id="history-heading">History</h2>
      {isPending && <p className={styles.muted}>Loading history…</p>}
      {isError && <p className={styles.muted}>Could not load the history.</p>}
      {revisions && revisions.length === 0 && (
        <p className={styles.muted}>No changes recorded yet.</p>
      )}
      {revisions && revisions.length > 0 && (
        <ol className={styles.revisionList}>
          {revisions.map((revision) => (
            <RevisionRow key={revision.revision} revision={revision} />
          ))}
        </ol>
      )}
    </section>
  )
}
