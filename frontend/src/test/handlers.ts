// MSW handlers: one per API endpoint, behaving like the Spring Boot back end closely enough
// for UI tests (Basic auth, roles, version checks, problem+json errors). Tests override a
// handler with `server.use(...)` when they need a special answer.
import { http, HttpResponse, type JsonBodyType } from 'msw'
import type {
  CurrentUser,
  Page,
  ReferralDetail,
  ReferralStatus,
  ReferralSummary,
  WaitingListEntry,
} from '../api/types'
import { CLINICS, USERS, db, findReferral } from './db'

/** Same origin as the app under test (jsdom's location). */
export const api = (path: string) => `${window.location.origin}/api${path}`

export function problem(
  status: number,
  title: string,
  detail: string,
  extra: Record<string, JsonBodyType> = {},
) {
  return HttpResponse.json(
    { type: 'about:blank', title, status, detail, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

/** Like Spring Security: a missing or wrong Basic credential is a 401 (no WWW-Authenticate). */
function authenticate(request: Request): CurrentUser | Response {
  const header = request.headers.get('Authorization') ?? ''
  if (header.startsWith('Basic ')) {
    const [username, password] = atob(header.slice('Basic '.length)).split(':')
    const user = USERS[username]
    if (user && user.password === password) return user.me
  }
  return problem(401, 'Unauthorized', 'Full authentication is required to access this resource.')
}

function requireRole(request: Request, role: string): CurrentUser | Response {
  const user = authenticate(request)
  if (user instanceof Response) return user
  if (!user.roles.includes(role)) {
    return problem(403, 'Forbidden', 'You do not have permission to perform this action.')
  }
  return user
}

function paged<T>(items: T[], url: URL): Page<T> {
  const page = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 20)
  return {
    content: items.slice(page * size, page * size + size),
    page: {
      size,
      number: page,
      totalElements: items.length,
      totalPages: Math.ceil(items.length / size),
    },
  }
}

function toSummary(r: ReferralDetail): ReferralSummary {
  return {
    id: r.id,
    status: r.status,
    patient: r.patient,
    clinic: r.clinic,
    suggestedPriority: r.suggestedPriority,
    confirmedPriority: r.confirmedPriority,
    priorityRank: r.priorityRank,
    receivedAt: r.receivedAt,
    triagedAt: r.triagedAt,
    version: r.version,
  }
}

const RANK = { URGENT: 1, SOON: 2, ROUTINE: 3 } as const

/** Checks shared by the version-guarded commands (`ReferralCommandService`). */
function guard(
  id: number,
  version: number | undefined,
  allowedFrom: ReferralStatus[],
  next: ReferralStatus,
): ReferralDetail | Response {
  const referral = findReferral(id)
  if (!referral) return problem(404, 'Not found', `Referral ${id} not found`)
  if (version !== undefined && version !== referral.version) {
    return problem(409, 'Stale version', 'The referral was changed since you loaded it; reload and retry.')
  }
  if (!allowedFrom.includes(referral.status)) {
    return problem(409, 'Invalid status transition', `Cannot move from ${referral.status} to ${next}`)
  }
  return referral
}

function update(id: number, changes: Partial<ReferralDetail>): ReferralDetail {
  const index = db.referrals.findIndex((r) => r.id === id)
  const updated = { ...db.referrals[index], ...changes, version: db.referrals[index].version + 1 }
  db.referrals[index] = updated
  return updated
}

export const handlers = [
  http.get(api('/me'), ({ request }) => {
    const user = authenticate(request)
    return user instanceof Response ? user : HttpResponse.json(user)
  }),

  http.get(api('/clinics'), ({ request }) => {
    const user = requireRole(request, 'CLINICIAN')
    return user instanceof Response ? user : HttpResponse.json(CLINICS)
  }),

  http.get(api('/waiting-list'), ({ request }) => {
    const user = requireRole(request, 'CLINICIAN')
    if (user instanceof Response) return user
    const url = new URL(request.url)
    const clinicId = Number(url.searchParams.get('clinicId'))
    const entries: WaitingListEntry[] = db.referrals
      .filter((r) => r.status === 'TRIAGED' && r.clinic.id === clinicId)
      .sort(
        (a, b) =>
          (a.priorityRank ?? 9) - (b.priorityRank ?? 9) || a.receivedAt.localeCompare(b.receivedAt),
      )
      .map((r) => ({
        id: r.id,
        patient: r.patient,
        confirmedPriority: r.confirmedPriority,
        priorityRank: r.priorityRank,
        receivedAt: r.receivedAt,
        triagedBy: r.triagedBy,
      }))
    return HttpResponse.json(paged(entries, url))
  }),

  http.get(api('/referrals'), ({ request }) => {
    const user = requireRole(request, 'CLINICIAN')
    if (user instanceof Response) return user
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    const priority = url.searchParams.get('priority')
    const clinicId = url.searchParams.get('clinicId')
    const sort = url.searchParams.get('sort') ?? 'receivedAt,desc'
    const direction = sort.endsWith(',asc') ? 1 : -1
    const items = db.referrals
      .filter((r) => !status || r.status === status)
      .filter((r) => !priority || r.confirmedPriority === priority)
      .filter((r) => !clinicId || r.clinic.id === Number(clinicId))
      .sort((a, b) => direction * a.receivedAt.localeCompare(b.receivedAt))
      .map(toSummary)
    return HttpResponse.json(paged(items, url))
  }),

  http.get(api('/referrals/:id'), ({ request, params }) => {
    const user = requireRole(request, 'CLINICIAN')
    if (user instanceof Response) return user
    const referral = findReferral(Number(params.id))
    return referral
      ? HttpResponse.json(referral)
      : problem(404, 'Not found', `Referral ${String(params.id)} not found`)
  }),

  http.post(api('/referrals/:id/triage-suggestion'), ({ request, params }) => {
    const user = requireRole(request, 'CLINICIAN')
    if (user instanceof Response) return user
    const id = Number(params.id)
    const checked = guard(id, undefined, ['RECEIVED', 'AI_SUGGESTED'], 'AI_SUGGESTED')
    if (checked instanceof Response) return checked
    return HttpResponse.json(
      update(id, {
        status: 'AI_SUGGESTED',
        aiSummary: 'Stable long-standing symptoms; routine review is appropriate.',
        suggestedPriority: 'ROUTINE',
        suggestionSource: 'AI',
        suggestionConfidence: 0.72,
        promptVersion: 'triage-v1',
        redFlags: [],
      }),
    )
  }),

  http.post(api('/referrals/:id/triage'), async ({ request, params }) => {
    const user = requireRole(request, 'CLINICIAN')
    if (user instanceof Response) return user
    const body = (await request.json()) as { priority?: string; note?: string | null; version?: number }
    const errors = []
    if (!body.priority) errors.push({ field: 'priority', message: 'must not be null' })
    if (body.note && body.note.length > 500) {
      errors.push({ field: 'note', message: 'size must be between 0 and 500' })
    }
    if (errors.length) return problem(400, 'Validation failed', 'One or more fields are invalid.', { errors })
    const id = Number(params.id)
    const checked = guard(id, body.version, ['AI_SUGGESTED'], 'TRIAGED')
    if (checked instanceof Response) return checked
    const priority = body.priority as keyof typeof RANK
    return HttpResponse.json(
      update(id, {
        status: 'TRIAGED',
        confirmedPriority: priority,
        priorityRank: RANK[priority],
        triageNote: body.note ?? null,
        triagedBy: user.username,
        triagedAt: '2026-11-23T10:00:00Z',
      }),
    )
  }),

  http.post(api('/referrals/:id/reject'), async ({ request, params }) => {
    const user = requireRole(request, 'CLINICIAN')
    if (user instanceof Response) return user
    const body = (await request.json()) as { version?: number }
    const id = Number(params.id)
    const checked = guard(id, body.version, ['RECEIVED', 'AI_SUGGESTED', 'TRIAGED'], 'REJECTED')
    if (checked instanceof Response) return checked
    return HttpResponse.json(update(id, { status: 'REJECTED' }))
  }),

  http.post(api('/referrals/:id/appointment'), async ({ request, params }) => {
    const user = requireRole(request, 'ADMIN')
    if (user instanceof Response) return user
    const body = (await request.json()) as { startsAt?: string }
    if (!body.startsAt || new Date(body.startsAt).getTime() <= Date.now()) {
      return problem(400, 'Validation failed', 'One or more fields are invalid.', {
        errors: [{ field: 'startsAt', message: 'must be a future date' }],
      })
    }
    const id = Number(params.id)
    const checked = guard(id, undefined, ['TRIAGED'], 'BOOKED')
    if (checked instanceof Response) return checked
    update(id, { status: 'BOOKED' })
    return HttpResponse.json(
      { id: 9001, referralId: id, startsAt: body.startsAt, status: 'BOOKED' },
      { status: 201 },
    )
  }),

  http.post(api('/referrals/import'), async ({ request }) => {
    const user = requireRole(request, 'ADMIN')
    if (user instanceof Response) return user
    const contentType = request.headers.get('Content-Type') ?? ''
    const text = await request.text()
    const reject = (detail: string, errors?: { field: string; message: string }[]) => {
      const deadLetterId = db.nextDeadLetterId++
      db.deadLetters.unshift({
        id: deadLetterId,
        source: contentType.startsWith('application/xml') ? 'XML' : 'JSON',
        payload: text,
        error: detail,
        receivedAt: '2026-11-23T10:00:00Z',
      })
      return problem(422, 'Invalid referral message', detail, {
        deadLetterId,
        ...(errors ? { errors } : {}),
      })
    }

    let externalRef: string | null = null
    if (contentType.startsWith('application/xml')) {
      externalRef = /externalRef="([^"]+)"/.exec(text)?.[1] ?? null
      if (!text.includes('<referral') || !externalRef) return reject('XML does not match referral.xsd')
    } else if (contentType.startsWith('application/json')) {
      let parsed: Record<string, unknown>
      try {
        parsed = JSON.parse(text) as Record<string, unknown>
      } catch {
        return reject('Malformed JSON')
      }
      const missing = ['externalRef', 'clinicCode', 'patient', 'clinicalNotes'].filter(
        (field) => !parsed[field],
      )
      if (missing.length) {
        return reject(
          'The referral message is invalid.',
          missing.map((field) => ({ field, message: 'must not be blank' })),
        )
      }
      externalRef = String(parsed.externalRef)
    } else {
      return problem(415, 'Unsupported Media Type', `Content-Type '${contentType}' is not supported.`)
    }

    const existing = db.importedRefs.get(externalRef)
    if (existing !== undefined) return HttpResponse.json({ id: existing, created: false })
    const id = db.nextReferralId++
    db.importedRefs.set(externalRef, id)
    return HttpResponse.json({ id, created: true }, { status: 201 })
  }),

  http.get(api('/dead-letters'), ({ request }) => {
    const user = requireRole(request, 'ADMIN')
    if (user instanceof Response) return user
    return HttpResponse.json(paged(db.deadLetters, new URL(request.url)))
  }),
]
