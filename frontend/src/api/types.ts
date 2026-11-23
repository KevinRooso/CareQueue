// TypeScript mirrors of the back-end DTO records. The Java records are the source of truth:
//   backend/src/main/java/com/carequeue/**/api/*.java
// Java `Instant` arrives as an ISO-8601 string, `BigDecimal` as a JSON number, and any
// object-typed field (String, Short, enums, lists) can be null.

export type Priority = 'URGENT' | 'SOON' | 'ROUTINE'
export type ReferralStatus = 'RECEIVED' | 'AI_SUGGESTED' | 'TRIAGED' | 'BOOKED' | 'REJECTED'
export type ReferralSource = 'JSON' | 'XML' | 'MANUAL'
export type SuggestionSource = 'AI' | 'RULE_BASED_FALLBACK'
export type ImportFormat = 'JSON' | 'XML'
export type AppointmentStatus = 'BOOKED' | 'CANCELLED'
export type Role = 'ADMIN' | 'CLINICIAN'

export const PRIORITIES: readonly Priority[] = ['URGENT', 'SOON', 'ROUTINE']
export const REFERRAL_STATUSES: readonly ReferralStatus[] = [
  'RECEIVED',
  'AI_SUGGESTED',
  'TRIAGED',
  'BOOKED',
  'REJECTED',
]

/** `CurrentUserController.MeResponse` – GET /api/me */
export interface CurrentUser {
  username: string
  roles: string[]
}

/** `PatientSummaryDto` */
export interface PatientSummary {
  mrn: string
  firstName: string
  lastName: string
}

/** `ClinicSummaryDto` – also the item type of GET /api/clinics */
export interface ClinicSummary {
  id: number
  code: string
  name: string
  specialty: string
}

/** `ReferralSummaryDto` – items of GET /api/referrals */
export interface ReferralSummary {
  id: number
  status: ReferralStatus
  patient: PatientSummary
  clinic: ClinicSummary
  suggestedPriority: Priority | null
  confirmedPriority: Priority | null
  priorityRank: number | null
  receivedAt: string
  triagedAt: string | null
  version: number
}

/** `ReferralDetailDto` – GET /api/referrals/{id} and every referral command response */
export interface ReferralDetail {
  id: number
  patient: PatientSummary
  clinic: ClinicSummary
  referringGp: string
  externalRef: string | null
  source: ReferralSource
  clinicalNotes: string
  rawMessage: string | null
  status: ReferralStatus
  aiSummary: string | null
  suggestedPriority: Priority | null
  suggestionSource: SuggestionSource | null
  /** 0..1 */
  suggestionConfidence: number | null
  promptVersion: string | null
  redFlags: string[] | null
  confirmedPriority: Priority | null
  priorityRank: number | null
  triageNote: string | null
  triagedBy: string | null
  triagedAt: string | null
  receivedAt: string
  version: number
}

/** `WaitingListEntryDto` – items of GET /api/waiting-list (already ordered by the server) */
export interface WaitingListEntry {
  id: number
  patient: PatientSummary
  confirmedPriority: Priority | null
  priorityRank: number | null
  receivedAt: string
  triagedBy: string | null
}

/** `DeadLetterDto` – items of GET /api/dead-letters */
export interface DeadLetter {
  id: number
  source: ImportFormat
  payload: string
  error: string
  receivedAt: string
}

/** `AppointmentDto` – 201 body of POST /api/referrals/{id}/appointment */
export interface Appointment {
  id: number
  referralId: number
  startsAt: string
  status: AppointmentStatus
}

/** Body of POST /api/referrals/import (201 created, 200 already imported) */
export interface ImportResult {
  id: number
  created: boolean
}

/** Spring Data `PagedModel` (VIA_DTO serialisation) */
export interface Page<T> {
  content: T[]
  page: {
    size: number
    /** zero-based */
    number: number
    totalElements: number
    totalPages: number
  }
}

/** `ConfirmTriageRequest` */
export interface ConfirmTriageRequest {
  priority: Priority
  note: string | null
  version: number
}

/** `RejectReferralRequest` */
export interface RejectReferralRequest {
  version: number
}

/** `BookAppointmentRequest` – `startsAt` is an ISO-8601 instant in the future */
export interface BookAppointmentRequest {
  startsAt: string
}

/** One entry of the `errors` array on a 400 / 422 problem */
export interface FieldChange {
  field: string
  from: string | null
  to: string | null
}

/** US-9: one row of a referral's audit trail (ADMIN). */
export interface ReferralRevision {
  revision: number
  timestamp: string
  username: string
  type: 'CREATED' | 'UPDATED' | 'DELETED'
  changes: FieldChange[]
}

export interface FieldError {
  field: string
  message: string
}

/** RFC 9457 problem+json as produced by `GlobalExceptionHandler` */
export interface Problem {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  errors?: FieldError[]
  /** Only on a 422 from the import endpoint */
  deadLetterId?: number
}

export interface ReferralFilters {
  status?: ReferralStatus
  priority?: Priority
  clinicId?: number
  /** zero-based */
  page: number
  size: number
  /** e.g. `receivedAt,desc`; the API allows receivedAt, triagedAt, status, priorityRank */
  sort: string
}
