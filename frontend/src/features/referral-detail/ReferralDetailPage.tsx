import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { isApiError } from '../../api/client'
import type { ReferralDetail } from '../../api/types'
import { useHasRole } from '../../auth/useAuth'
import { ErrorBanner } from '../../components/ErrorBanner'
import { formatDateTime, patientName } from '../../components/format'
import { PageHeader } from '../../components/PageHeader'
import { parseId } from '../../components/params'
import { PriorityBadge } from '../../components/PriorityBadge'
import { Spinner } from '../../components/Spinner'
import { StatusBadge } from '../../components/StatusBadge'
import ui from '../../components/ui.module.css'
import { actionsFor } from './actions'
import { BookAppointmentForm } from './BookAppointmentForm'
import { ConfirmTriageForm } from './ConfirmTriageForm'
import { useReferral } from './hooks'
import type { Notice } from './notice'
import { RejectAction } from './RejectAction'
import { SuggestionPanel } from './SuggestionPanel'
import styles from './ReferralDetailPage.module.css'

const SOURCE_LABEL: Record<ReferralDetail['source'], string> = {
  JSON: 'GP system (JSON)',
  XML: 'GP system (XML)',
  MANUAL: 'Entered manually',
}

const backLink = <Link to="/referrals">Back to referral inbox</Link>

/** US-4, US-5, US-6, US-8: one referral, its AI suggestion and the clinician's decision. */
export function ReferralDetailPage() {
  const id = parseId(useParams().id)
  if (id === undefined) return <ReferralNotFound />
  // A new id means a fresh page: no notices or half-filled forms carried over.
  return <ReferralDetailView key={id} id={id} />
}

function ReferralNotFound() {
  return (
    <>
      <PageHeader title="Referral not found" context={backLink} />
      <p>
        There is no referral at this address. It may have been typed wrongly. Find it in the{' '}
        <Link to="/referrals">referral inbox</Link>.
      </p>
    </>
  )
}

function ReferralDetailView({ id }: { id: number }) {
  const referral = useReferral(id)
  const [notice, setNotice] = useState<Notice | null>(null)

  if (referral.isPending) {
    return (
      <>
        <PageHeader title="Referral" context={backLink} />
        <Spinner label="Loading referral" />
      </>
    )
  }
  if (referral.isError) {
    if (isApiError(referral.error, 404)) return <ReferralNotFound />
    return (
      <>
        <PageHeader title="Referral" context={backLink} />
        <ErrorBanner error={referral.error}>
          <p>
            <button type="button" className={ui.link} onClick={() => referral.refetch()}>
              Try again
            </button>
          </p>
        </ErrorBanner>
      </>
    )
  }

  const detail = referral.data
  return (
    <>
      <PageHeader
        title={patientName(detail.patient)}
        context={backLink}
        description={`Referral to ${detail.clinic.name}`}
        actions={
          <div className={styles.headerBadges}>
            <StatusBadge status={detail.status} />
            {detail.confirmedPriority && (
              <PriorityBadge priority={detail.confirmedPriority} size="lg" />
            )}
          </div>
        }
      />
      <div className={styles.layout}>
        <ReferralLetter referral={detail} />
        <TriageColumn referral={detail} notice={notice} onNotice={setNotice} />
      </div>
    </>
  )
}

function ReferralLetter({ referral }: { referral: ReferralDetail }) {
  return (
    <article className={styles.letter} aria-labelledby="letter-heading">
      <h2 id="letter-heading" className="visually-hidden">
        Referral details
      </h2>
      <dl className={styles.details}>
        <div>
          <dt>Patient</dt>
          <dd>{patientName(referral.patient)}</dd>
        </div>
        <div>
          <dt>MRN</dt>
          <dd className="numeric">{referral.patient.mrn}</dd>
        </div>
        <div>
          <dt>Clinic</dt>
          <dd>
            {referral.clinic.name}
            <span className={styles.subtle}> ({referral.clinic.specialty})</span>
          </dd>
        </div>
        <div>
          <dt>Referring GP</dt>
          <dd>{referral.referringGp}</dd>
        </div>
        <div>
          <dt>Received</dt>
          <dd>
            <time dateTime={referral.receivedAt}>{formatDateTime(referral.receivedAt)}</time>
          </dd>
        </div>
        <div>
          <dt>Arrived from</dt>
          <dd>
            {SOURCE_LABEL[referral.source]}
            {referral.externalRef && (
              <span className={styles.subtle}>, reference {referral.externalRef}</span>
            )}
          </dd>
        </div>
      </dl>

      <section className={styles.notesSection} aria-labelledby="notes-heading">
        <h2 id="notes-heading">Clinical notes</h2>
        <p className={styles.notes}>{referral.clinicalNotes}</p>
      </section>

      {referral.confirmedPriority && (
        <section className={styles.decision} aria-labelledby="decision-heading">
          <h2 id="decision-heading">Clinician&rsquo;s decision</h2>
          <dl className={styles.details}>
            <div>
              <dt>Confirmed priority</dt>
              <dd>
                <PriorityBadge priority={referral.confirmedPriority} />
              </dd>
            </div>
            <div>
              <dt>Triaged by</dt>
              <dd>{referral.triagedBy ?? 'Unknown'}</dd>
            </div>
            {referral.triagedAt && (
              <div>
                <dt>Triaged</dt>
                <dd>
                  <time dateTime={referral.triagedAt}>{formatDateTime(referral.triagedAt)}</time>
                </dd>
              </div>
            )}
            <div className={styles.wide}>
              <dt>Note</dt>
              <dd>{referral.triageNote || 'No note.'}</dd>
            </div>
          </dl>
        </section>
      )}
    </article>
  )
}

function TriageColumn({
  referral,
  notice,
  onNotice,
}: {
  referral: ReferralDetail
  notice: Notice | null
  onNotice: (notice: Notice | null) => void
}) {
  const isAdmin = useHasRole('ADMIN')
  const actions = actionsFor(referral.status)

  return (
    <div className={styles.triage}>
      {/* Always in the DOM so screen readers announce each new result. */}
      <div className={styles.liveRegion} role="status" aria-live="polite">
        {notice && (
          <p className={styles.notice} data-tone={notice.tone}>
            {notice.text}
          </p>
        )}
      </div>

      <SuggestionPanel referral={referral} canSuggest={actions.canSuggest} onNotice={onNotice} />

      {actions.canConfirm && (
        <section className={styles.panel} aria-labelledby="confirm-heading">
          <h2 id="confirm-heading">Confirm priority</h2>
          {/* key: a new version (e.g. after a 409 reload) resets the form to the new data. */}
          <ConfirmTriageForm key={referral.version} referral={referral} onNotice={onNotice} />
        </section>
      )}
      {referral.status === 'RECEIVED' && (
        <section className={styles.panel} aria-labelledby="confirm-heading">
          <h2 id="confirm-heading">Confirm priority</h2>
          <p className={styles.muted}>
            Get an AI suggestion first. You then confirm it or choose a different priority.
          </p>
        </section>
      )}

      {actions.canBook && (
        <section className={styles.panel} aria-labelledby="book-heading">
          <h2 id="book-heading">Book appointment</h2>
          {isAdmin ? (
            <BookAppointmentForm key={referral.version} referral={referral} onNotice={onNotice} />
          ) : (
            <p className={styles.muted}>
              This referral is on the waiting list. A clinic manager books the appointment.
            </p>
          )}
        </section>
      )}

      {actions.canReject && (
        <section className={styles.panelQuiet} aria-labelledby="reject-heading">
          <h2 id="reject-heading">Reject</h2>
          <RejectAction key={referral.version} referral={referral} onNotice={onNotice} />
        </section>
      )}
    </div>
  )
}
