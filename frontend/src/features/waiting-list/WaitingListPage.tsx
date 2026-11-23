import { Link, useSearchParams } from 'react-router'
import type { ClinicSummary } from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorBanner } from '../../components/ErrorBanner'
import { formatDateTime, patientName } from '../../components/format'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { parseId, parsePage, withPage } from '../../components/params'
import { PriorityBadge } from '../../components/PriorityBadge'
import { Spinner } from '../../components/Spinner'
import table from '../../components/table.module.css'
import ui from '../../components/ui.module.css'
import { useClinics } from '../clinics/hooks'
import { useWaitingList } from './hooks'
import styles from './WaitingListPage.module.css'

/** US-7: a clinic's waiting list, in the order patients will be seen. */
export function WaitingListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const clinics = useClinics()

  // The clinic comes from the URL (?clinicId=2); without one we show the first clinic.
  const clinicList = clinics.data ?? []
  const requestedId = parseId(searchParams.get('clinicId'))
  const clinic = clinicList.find((c) => c.id === requestedId) ?? clinicList[0]
  const page = parsePage(searchParams.get('page'))

  function selectClinic(id: string) {
    // A new clinic starts again at page 1.
    setSearchParams({ clinicId: id })
  }

  return (
    <>
      <PageHeader
        title="Waiting list"
        description="Triaged referrals in the order patients will be seen: priority first, then the date the referral arrived."
        actions={
          clinicList.length > 0 && clinic ? (
            <div className={ui.field}>
              <label className={ui.label} htmlFor="waiting-list-clinic">
                Clinic
              </label>
              <select
                id="waiting-list-clinic"
                className={`${ui.select} ${styles.clinicSelect}`}
                value={clinic.id}
                onChange={(event) => selectClinic(event.target.value)}
              >
                {clinicList.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
          ) : null
        }
      />

      {clinics.isPending && <Spinner label="Loading clinics" />}
      {clinics.isError && (
        <ErrorBanner error={clinics.error}>
          <p>
            <button type="button" className={ui.link} onClick={() => clinics.refetch()}>
              Try again
            </button>
          </p>
        </ErrorBanner>
      )}
      {clinics.isSuccess && clinicList.length === 0 && (
        <EmptyState title="No clinics are set up yet">
          Clinics are reference data loaded by the server. Ask an administrator to add them.
        </EmptyState>
      )}
      {clinic && (
        <WaitingListTable
          clinic={clinic}
          page={page}
          onPageChange={(next) => setSearchParams(withPage(searchParams, next))}
        />
      )}
    </>
  )
}

function WaitingListTable({
  clinic,
  page,
  onPageChange,
}: {
  clinic: ClinicSummary
  page: number
  onPageChange: (page: number) => void
}) {
  const list = useWaitingList(clinic.id, page)

  if (list.isPending) return <Spinner label="Loading waiting list" />
  if (list.isError) {
    return (
      <ErrorBanner error={list.error}>
        <p>
          <button type="button" className={ui.link} onClick={() => list.refetch()}>
            Try again
          </button>
        </p>
      </ErrorBanner>
    )
  }

  const { content, page: meta } = list.data
  if (meta.totalElements === 0) {
    return (
      <EmptyState title={`Nobody is waiting for ${clinic.name}`}>
        Referrals join this list when a clinician confirms their priority. Open the{' '}
        <Link to={`/referrals?clinicId=${clinic.id}`}>referral inbox</Link> to triage new ones.
      </EmptyState>
    )
  }

  const firstPosition = meta.number * meta.size
  return (
    <>
      <div className={table.wrap}>
        <table className={`${table.table} ${list.isPlaceholderData ? table.updating : ''}`}>
          <caption className={table.caption}>
            <span className={table.captionStrong}>{clinic.name}</span>: {meta.totalElements}{' '}
            {meta.totalElements === 1 ? 'patient' : 'patients'} waiting
          </caption>
          <thead>
            <tr>
              <th scope="col">Position</th>
              <th scope="col">Priority</th>
              <th scope="col">Patient</th>
              <th scope="col">MRN</th>
              <th scope="col">Received</th>
              <th scope="col">Triaged by</th>
            </tr>
          </thead>
          <tbody>
            {content.map((entry, index) => (
              <tr key={entry.id} data-priority={entry.confirmedPriority ?? undefined}>
                <td className={styles.position}>{firstPosition + index + 1}</td>
                <td>
                  {entry.confirmedPriority ? (
                    <PriorityBadge priority={entry.confirmedPriority} />
                  ) : (
                    <span className={table.muted}>Not set</span>
                  )}
                </td>
                <th scope="row" className={styles.patient}>
                  <Link className={table.rowLink} to={`/referrals/${entry.id}`}>
                    {patientName(entry.patient)}
                  </Link>
                </th>
                <td className={table.numeric}>{entry.patient.mrn}</td>
                <td className={table.numeric}>
                  <time dateTime={entry.receivedAt}>{formatDateTime(entry.receivedAt)}</time>
                </td>
                <td className={table.muted}>{entry.triagedBy ?? 'Unknown'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination
        page={meta.number}
        totalPages={meta.totalPages}
        onPageChange={onPageChange}
        label="Waiting list pages"
      />
    </>
  )
}
