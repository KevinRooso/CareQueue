import { sessionStore } from '../auth/sessionStore'
import type {
  Appointment,
  BookAppointmentRequest,
  ClinicSummary,
  ConfirmTriageRequest,
  CurrentUser,
  DeadLetter,
  FieldError,
  ImportResult,
  Page,
  Problem,
  ReferralDetail,
  ReferralFilters,
  ReferralRevision,
  ReferralSummary,
  RejectReferralRequest,
  WaitingListEntry,
} from './types'

/**
 * Every failed API call ends up as an ApiError. For problem+json responses the fields come
 * straight from the server (`GlobalExceptionHandler`); for anything else we fill in a
 * generic title so the UI always has something sensible to show.
 */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string
  readonly errors: FieldError[]
  readonly deadLetterId?: number

  constructor(init: {
    status: number
    title: string
    detail?: string
    errors?: FieldError[]
    deadLetterId?: number
  }) {
    super(init.detail ? `${init.title}: ${init.detail}` : init.title)
    this.name = 'ApiError'
    this.status = init.status
    this.title = init.title
    this.detail = init.detail ?? ''
    this.errors = init.errors ?? []
    this.deadLetterId = init.deadLetterId
  }

  /** The server's message for one field of a 400/422, if any. */
  fieldError(field: string): string | undefined {
    return this.errors.find((e) => e.field === field)?.message
  }
}

export function isApiError(error: unknown, status?: number): error is ApiError {
  return error instanceof ApiError && (status === undefined || error.status === status)
}

type QueryValue = string | number | undefined | null

interface RequestOptions {
  method?: 'GET' | 'POST'
  query?: Record<string, QueryValue>
  /** Serialised with JSON.stringify unless `rawBody` is used. */
  json?: unknown
  rawBody?: string
  contentType?: string
  signal?: AbortSignal
  /**
   * Explicit credentials (used only by login to verify a username/password before
   * they are stored). A 401 on such a request does not end the current session.
   */
  authorization?: string
}

function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  // Absolute URL so the same code works in the browser (same origin, Vite proxy in dev)
  // and in Node-based tests.
  const url = new URL(`/api${path}`, window.location.origin)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  }
  return url.toString()
}

function isFieldErrorList(value: unknown): value is FieldError[] {
  return (
    Array.isArray(value) &&
    value.every(
      (e) =>
        typeof e === 'object' &&
        e !== null &&
        typeof (e as FieldError).field === 'string' &&
        typeof (e as FieldError).message === 'string',
    )
  )
}

async function toApiError(response: Response): Promise<ApiError> {
  const fallbackTitle = response.statusText || `Request failed (${response.status})`
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('json')) {
    try {
      const problem = (await response.json()) as Problem
      return new ApiError({
        status: response.status,
        title: typeof problem.title === 'string' ? problem.title : fallbackTitle,
        detail: typeof problem.detail === 'string' ? problem.detail : undefined,
        errors: isFieldErrorList(problem.errors) ? problem.errors : [],
        deadLetterId: typeof problem.deadLetterId === 'number' ? problem.deadLetterId : undefined,
      })
    } catch {
      // Body was not valid JSON – fall through to the generic error.
    }
  }
  return new ApiError({ status: response.status, title: fallbackTitle })
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({ Accept: 'application/json, application/problem+json' })
  const storedAuthorization = options.authorization ? null : sessionStore.authorization()
  const authorization = options.authorization ?? storedAuthorization
  if (authorization) headers.set('Authorization', authorization)

  let body: string | undefined
  if (options.rawBody !== undefined) {
    body = options.rawBody
    headers.set('Content-Type', options.contentType ?? 'text/plain')
  } else if (options.json !== undefined) {
    body = JSON.stringify(options.json)
    headers.set('Content-Type', 'application/json')
  }

  let response: Response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body,
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError({
      status: 0,
      title: 'Cannot reach the CareQueue server',
      detail: 'Check your connection and that the server is running, then try again.',
    })
  }

  if (!response.ok) {
    // Our stored credentials were rejected: drop them and let the auth layer redirect.
    if (response.status === 401 && storedAuthorization) sessionStore.expire()
    throw await toApiError(response)
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

// ---- Typed endpoint functions -------------------------------------------------------------

export const api = {
  /** Verify a username/password pair. Does not touch the stored session. */
  verifyCredentials(authorization: string): Promise<CurrentUser> {
    return request<CurrentUser>('/me', { authorization })
  },

  getMe(signal?: AbortSignal): Promise<CurrentUser> {
    return request<CurrentUser>('/me', { signal })
  },

  getClinics(signal?: AbortSignal): Promise<ClinicSummary[]> {
    return request<ClinicSummary[]>('/clinics', { signal })
  },

  getWaitingList(
    clinicId: number,
    page: number,
    size: number,
    signal?: AbortSignal,
  ): Promise<Page<WaitingListEntry>> {
    return request<Page<WaitingListEntry>>('/waiting-list', {
      query: { clinicId, page, size },
      signal,
    })
  },

  getReferrals(filters: ReferralFilters, signal?: AbortSignal): Promise<Page<ReferralSummary>> {
    return request<Page<ReferralSummary>>('/referrals', {
      query: {
        status: filters.status,
        priority: filters.priority,
        clinicId: filters.clinicId,
        page: filters.page,
        size: filters.size,
        sort: filters.sort,
      },
      signal,
    })
  },

  getReferral(id: number, signal?: AbortSignal): Promise<ReferralDetail> {
    return request<ReferralDetail>(`/referrals/${id}`, { signal })
  },

  getReferralHistory(id: number, signal?: AbortSignal): Promise<ReferralRevision[]> {
    return request<ReferralRevision[]>(`/referrals/${id}/history`, { signal })
  },

  requestTriageSuggestion(id: number): Promise<ReferralDetail> {
    return request<ReferralDetail>(`/referrals/${id}/triage-suggestion`, { method: 'POST' })
  },

  confirmTriage(id: number, body: ConfirmTriageRequest): Promise<ReferralDetail> {
    return request<ReferralDetail>(`/referrals/${id}/triage`, { method: 'POST', json: body })
  },

  rejectReferral(id: number, body: RejectReferralRequest): Promise<ReferralDetail> {
    return request<ReferralDetail>(`/referrals/${id}/reject`, { method: 'POST', json: body })
  },

  bookAppointment(id: number, body: BookAppointmentRequest): Promise<Appointment> {
    return request<Appointment>(`/referrals/${id}/appointment`, { method: 'POST', json: body })
  },

  /** 201 `{id, created: true}`, 200 `{id, created: false}`; a 422 throws an ApiError with `deadLetterId`. */
  importReferral(message: string, contentType: ImportContentType): Promise<ImportResult> {
    return request<ImportResult>('/referrals/import', {
      method: 'POST',
      rawBody: message,
      contentType,
    })
  },

  getDeadLetters(page: number, size: number, signal?: AbortSignal): Promise<Page<DeadLetter>> {
    return request<Page<DeadLetter>>('/dead-letters', { query: { page, size }, signal })
  },
}

export type ImportContentType = 'application/json' | 'application/xml'
