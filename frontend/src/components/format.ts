import type { PatientSummary, Priority, ReferralStatus, SuggestionSource } from '../api/types'

export const PRIORITY_LABEL: Record<Priority, string> = {
  URGENT: 'Urgent',
  SOON: 'Soon',
  ROUTINE: 'Routine',
}

/** What each priority means for the patient (PRD §6). */
export const PRIORITY_MEANING: Record<Priority, string> = {
  URGENT: 'Seen within 2 weeks',
  SOON: 'Seen within 3 months',
  ROUTINE: 'Routine waiting list',
}

export const STATUS_LABEL: Record<ReferralStatus, string> = {
  RECEIVED: 'Received',
  AI_SUGGESTED: 'AI suggested',
  TRIAGED: 'Triaged',
  BOOKED: 'Booked',
  REJECTED: 'Rejected',
}

export const SUGGESTION_SOURCE_LABEL: Record<SuggestionSource, string> = {
  AI: 'AI model',
  RULE_BASED_FALLBACK: 'Rule-based fallback',
}

export function patientName(patient: PatientSummary): string {
  return `${patient.firstName} ${patient.lastName}`
}

// Irish English formats: the service is modelled on an Irish hospital (PRD personas).
const dateTimeFormat = new Intl.DateTimeFormat('en-IE', { dateStyle: 'medium', timeStyle: 'short' })
const dateFormat = new Intl.DateTimeFormat('en-IE', { dateStyle: 'medium' })

export function formatDateTime(iso: string): string {
  return dateTimeFormat.format(new Date(iso))
}

export function formatDate(iso: string): string {
  return dateFormat.format(new Date(iso))
}

/** 0.82 → "82%" */
export function formatConfidence(confidence: number): string {
  return `${Math.round(confidence * 100)}%`
}

/**
 * `<input type="datetime-local">` gives local wall-clock time without a zone
 * ("2030-03-01T09:00"). `new Date()` reads that as local time, and `toISOString()` turns it
 * into the UTC instant the API expects ("2030-03-01T09:00:00.000Z" in Dublin in winter).
 */
export function localDateTimeToInstant(value: string): string {
  return new Date(value).toISOString()
}

/** A Date as the value format of `<input type="datetime-local">` (local time, minutes). */
export function toDateTimeLocalValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`
}
