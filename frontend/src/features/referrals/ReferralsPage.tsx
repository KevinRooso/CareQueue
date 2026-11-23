import { useId, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import {
  PRIORITIES,
  REFERRAL_STATUSES,
  type Priority,
  type ReferralFilters,
  type ReferralStatus,
} from '../../api/types'
import { EmptyState } from '../../components/EmptyState'
import { ErrorBanner } from '../../components/ErrorBanner'
import { formatDateTime, patientName, PRIORITY_LABEL, STATUS_LABEL } from '../../components/format'
import { PageHeader } from '../../components/PageHeader'
import { Pagination } from '../../components/Pagination'
import { parseId } from '../../components/params'
import { PriorityBadge } from '../../components/PriorityBadge'
import { Spinner } from '../../components/Spinner'
import { StatusBadge } from '../../components/StatusBadge'
import table from '../../components/table.module.css'
import ui from '../../components/ui.module.css'
import { useClinics } from '../clinics/hooks'
import { REFERRALS_PAGE_SIZE, useReferrals } from './hooks'
import styles from './ReferralsPage.module.css'

/**
 * "Needs triage" is the default view. The API filters on one status at a time, so this view
 * runs two queries (AI suggested, then received) and shows them as two sections, each with
 * its own paging. Merging two paged results into one table would make paging lie.
 */
const NEEDS_TRIAGE = 'needs-triage'
const ALL = 'all'
type StatusView = typeof NEEDS_TRIAGE | typeof ALL | ReferralStatus
type Order = 'oldest' | 'newest'

const ORDER_SORT: Record<Order, string> = {
  oldest: 'receivedAt,asc',
  newest: 'receivedAt,desc',
}

function parseStatusView(value: string | null): StatusView {
  if (value === ALL) return ALL
  if (value && (REFERRAL_STATUSES as readonly string[]).includes(value)) {
    return value as ReferralStatus
  }
  return NEEDS_TRIAGE
}

function parsePriority(value: string | null): Priority | undefined {
  return value && (PRIORITIES as readonly string[]).includes(value)
    ? (value as Priority)
    : undefined
}

type BaseFilters = Omit<ReferralFilters, 'page'>

/** US-4/US-5 entry point: the clinician's inbox of referrals. */
export function ReferralsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const clinics = useClinics()

  const view = parseStatusView(searchParams.get('status'))
  const clinicId = parseId(searchParams.get('clinicId'))
  const priority = parsePriority(searchParams.get('priority'))
  const order: Order = searchParams.get('order') === 'newest' ? 'newest' : 'oldest'

  function setFilter(name: string, value: string, defaultValue = '') {
    const next = new URLSearchParams(searchParams)
    if (value === defaultValue) next.delete(name)
    else next.set(name, value)
    setSearchParams(next)
  }

  const base: Omit<BaseFilters, 'status'> = {
    clinicId,
    priority,
    size: REFERRALS_PAGE_SIZE,
    sort: ORDER_SORT[order],
  }
  // Remount sections when the filters change so each starts again at page 1.
  const filterKey = `${view}|${clinicId ?? ''}|${priority ?? ''}|${order}`

  return (
    <>
      <PageHeader
        title="Referral inbox"
        description="New referrals waiting for a clinician. Get an AI suggestion, then confirm or change the priority."
      />

      <form
        className={styles.filters}
        role="search"
        aria-label="Filter referrals"
        onSubmit={(event) => event.preventDefault()}
      >
        <div className={ui.field}>
          <label className={ui.label} htmlFor="filter-status">
            Status
          </label>
          <select
            id="filter-status"
            className={ui.select}
            value={view}
            onChange={(event) => setFilter('status', event.target.value, NEEDS_TRIAGE)}
          >
            <option value={NEEDS_TRIAGE}>Needs triage</option>
            {REFERRAL_STATUSES.map((status) => (
              <option key={status} value={status}>
                {STATUS_LABEL[status]}
              </option>
            ))}
            <option value={ALL}>All statuses</option>
          </select>
        </div>

        <div className={ui.field}>
          <label className={ui.label} htmlFor="filter-clinic">
            Clinic
          </label>
          <select
            id="filter-clinic"
            className={ui.select}
            value={clinicId ?? ''}
            onChange={(event) => setFilter('clinicId', event.target.value)}
          >
            <option value="">All clinics</option>
            {(clinics.data ?? []).map((clinic) => (
              <option key={clinic.id} value={clinic.id}>
                {clinic.name}
              </option>
            ))}
          </select>
        </div>

        <div className={ui.field}>
          <label className={ui.label} htmlFor="filter-priority">
            Confirmed priority
          </label>
          <select
            id="filter-priority"
            className={ui.select}
            value={priority ?? ''}
            onChange={(event) => setFilter('priority', event.target.value)}
          >
            <option value="">Any</option>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_LABEL[p]}
              </option>
            ))}
          </select>
        </div>

        <div className={ui.field}>
          <label className={ui.label} htmlFor="filter-order">
            Date received
          </label>
          <select
            id="filter-order"
            className={ui.select}
            value={order}
            onChange={(event) => setFilter('order', event.target.value, 'oldest')}
          >
            <option value="oldest">Oldest first</option>
            <option value="newest">Newest first</option>
          </select>
        </div>
      </form>

      {view === NEEDS_TRIAGE ? (
        <div className={styles.sections}>
          <ReferralSection
            key={`suggested|${filterKey}`}
            heading="Ready to confirm"
            explanation="An AI suggestion is waiting for a clinician's decision."
            filters={{ ...base, status: 'AI_SUGGESTED' }}
            emptyTitle="No suggestions are waiting for a decision"
          />
          <ReferralSection
            key={`received|${filterKey}`}
            heading="Waiting for an AI suggestion"
            explanation="Open a referral and get an AI suggestion before confirming a priority."
            filters={{ ...base, status: 'RECEIVED' }}
            emptyTitle="No new referrals"
          />
        </div>
      ) : (
        <ReferralSection
          key={filterKey}
          filters={{ ...base, status: view === ALL ? undefined : view }}
          emptyTitle="No referrals match these filters"
        />
      )}
    </>
  )
}

function ReferralSection({
  heading,
  explanation,
  filters,
  emptyTitle,
}: {
  heading?: string
  explanation?: string
  filters: BaseFilters
  emptyTitle: string
}) {
  const headingId = useId()
  const [page, setPage] = useState(0)
  const referrals = useReferrals({ ...filters, page })
  const captionLabel = heading ?? (filters.status ? STATUS_LABEL[filters.status] : 'All referrals')

  let body
  if (referrals.isPending) {
    body = <Spinner label={`Loading ${captionLabel.toLowerCase()}`} />
  } else if (referrals.isError) {
    body = (
      <ErrorBanner error={referrals.error}>
        <p>
          <button type="button" className={ui.link} onClick={() => referrals.refetch()}>
            Try again
          </button>
        </p>
      </ErrorBanner>
    )
  } else if (referrals.data.page.totalElements === 0) {
    body = (
      <EmptyState title={emptyTitle}>
        Change the filters above, or check the{' '}
        <Link to="/waiting-list">waiting list</Link> for referrals that are already triaged.
      </EmptyState>
    )
  } else {
    const { content, page: meta } = referrals.data
    body = (
      <>
        <div className={table.wrap}>
          <table
            className={`${table.table} ${referrals.isPlaceholderData ? table.updating : ''}`}
          >
            <caption className={table.caption}>
              <span className={table.captionStrong}>{captionLabel}</span>:{' '}
              {meta.totalElements} {meta.totalElements === 1 ? 'referral' : 'referrals'},{' '}
              {filters.sort.endsWith('asc') ? 'oldest first' : 'newest first'}
            </caption>
            <thead>
              <tr>
                <th scope="col">Patient</th>
                <th scope="col">MRN</th>
                <th scope="col">Clinic</th>
                <th scope="col">Status</th>
                <th scope="col">Priority</th>
                <th scope="col">Received</th>
              </tr>
            </thead>
            <tbody>
              {content.map((referral) => (
                <tr key={referral.id} data-priority={referral.confirmedPriority ?? undefined}>
                  <th scope="row" className={styles.patient}>
                    <Link className={table.rowLink} to={`/referrals/${referral.id}`}>
                      {patientName(referral.patient)}
                    </Link>
                  </th>
                  <td className={table.numeric}>{referral.patient.mrn}</td>
                  <td>{referral.clinic.name}</td>
                  <td>
                    <StatusBadge status={referral.status} />
                  </td>
                  <td>
                    {referral.confirmedPriority ? (
                      <PriorityBadge priority={referral.confirmedPriority} />
                    ) : referral.suggestedPriority ? (
                      <PriorityBadge priority={referral.suggestedPriority} variant="suggested" />
                    ) : (
                      <span className={table.muted}>Not set</span>
                    )}
                  </td>
                  <td className={table.numeric}>
                    <time dateTime={referral.receivedAt}>
                      {formatDateTime(referral.receivedAt)}
                    </time>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <Pagination
          page={meta.number}
          totalPages={meta.totalPages}
          onPageChange={setPage}
          label={`${captionLabel} pages`}
        />
      </>
    )
  }

  if (!heading) return body
  return (
    <section className={styles.section} aria-labelledby={headingId}>
      <div className={styles.sectionHeader}>
        <h2 id={headingId}>{heading}</h2>
        {explanation && <p className={styles.explanation}>{explanation}</p>}
      </div>
      {body}
    </section>
  )
}
