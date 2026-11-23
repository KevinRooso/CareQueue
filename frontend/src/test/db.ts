// An in-memory fake of the back end's data for MSW handlers. Synthetic data only.
// `resetDb()` runs before every test, so each test starts from the same state.
import type {
  ClinicSummary,
  CurrentUser,
  DeadLetter,
  PatientSummary,
  ReferralDetail,
} from '../api/types'

export const CLINICS: ClinicSummary[] = [
  { id: 1, code: 'GASTRO', name: 'Gastroenterology', specialty: 'Gastroenterology' },
  { id: 2, code: 'DERMATOLOGY', name: 'Dermatology', specialty: 'Dermatology' },
  { id: 3, code: 'CARDIOLOGY', name: 'Cardiology', specialty: 'Cardiology' },
]

/** Test-only demo users. The passwords are fake and exist only in these tests. */
export const USERS: Record<string, { password: string; me: CurrentUser }> = {
  nurse: { password: 'nurse-test-only', me: { username: 'nurse', roles: ['CLINICIAN'] } },
  manager: {
    password: 'manager-test-only',
    me: { username: 'manager', roles: ['ADMIN', 'CLINICIAN'] },
  },
}

const patient = (mrn: string, firstName: string, lastName: string): PatientSummary => ({
  mrn,
  firstName,
  lastName,
})

function referral(overrides: Partial<ReferralDetail> & Pick<ReferralDetail, 'id'>): ReferralDetail {
  return {
    patient: patient('MRN-900000', 'Alex', 'Sample'),
    clinic: CLINICS[0],
    referringGp: 'Dr. A. Byrne, Riverside Medical Centre',
    externalRef: `GP-TEST-${overrides.id}`,
    source: 'JSON',
    clinicalNotes: 'Synthetic referral for tests.',
    rawMessage: null,
    status: 'RECEIVED',
    aiSummary: null,
    suggestedPriority: null,
    suggestionSource: null,
    suggestionConfidence: null,
    promptVersion: null,
    redFlags: null,
    confirmedPriority: null,
    priorityRank: null,
    triageNote: null,
    triagedBy: null,
    triagedAt: null,
    receivedAt: '2026-11-02T09:00:00Z',
    version: 0,
    ...overrides,
  }
}

function initialReferrals(): ReferralDetail[] {
  return [
    referral({
      id: 101,
      patient: patient('MRN-900001', 'Alex', 'Sample'),
      status: 'AI_SUGGESTED',
      clinicalNotes:
        '58-year-old with 6 weeks of change in bowel habit and 5kg unintentional weight loss.',
      aiSummary: 'Change in bowel habit with weight loss; needs prompt investigation.',
      suggestedPriority: 'URGENT',
      suggestionSource: 'AI',
      suggestionConfidence: 0.86,
      promptVersion: 'triage-v1',
      redFlags: ['unintentional weight loss'],
      receivedAt: '2026-11-03T08:30:00Z',
      version: 3,
    }),
    referral({
      id: 102,
      patient: patient('MRN-900002', 'Sam', 'Test'),
      clinic: CLINICS[1],
      clinicalNotes: 'Long-standing mild eczema on both hands. Requesting review of treatment.',
      receivedAt: '2026-11-01T10:00:00Z',
      version: 0,
    }),
    referral({
      id: 103,
      patient: patient('MRN-900003', 'Jamie', 'Placeholder'),
      status: 'TRIAGED',
      suggestedPriority: 'URGENT',
      suggestionSource: 'AI',
      confirmedPriority: 'URGENT',
      priorityRank: 1,
      triagedBy: 'nurse',
      triagedAt: '2026-11-04T11:00:00Z',
      receivedAt: '2026-11-02T09:00:00Z',
      version: 5,
    }),
    referral({
      id: 104,
      patient: patient('MRN-900004', 'Robin', 'Example'),
      status: 'TRIAGED',
      confirmedPriority: 'ROUTINE',
      priorityRank: 3,
      triagedBy: 'nurse',
      triagedAt: '2026-11-04T12:00:00Z',
      receivedAt: '2026-10-28T09:00:00Z',
      version: 2,
    }),
    referral({
      id: 105,
      patient: patient('MRN-900005', 'Casey', 'Demo'),
      clinic: CLINICS[1],
      status: 'TRIAGED',
      confirmedPriority: 'SOON',
      priorityRank: 2,
      triagedBy: 'manager',
      triagedAt: '2026-11-05T12:00:00Z',
      receivedAt: '2026-10-30T09:00:00Z',
      version: 1,
    }),
  ]
}

function initialDeadLetters(): DeadLetter[] {
  return [
    {
      id: 7,
      source: 'XML',
      payload: `<referral externalRef="GP-TEST-BAD-7"><referringGp>Dr. Test</referringGp>${'<clinicalNotes>Synthetic notes. </clinicalNotes>'.repeat(4)}</referral>`,
      error: "cvc-complex-type.2.4.a: Invalid content starting with element 'clinicalNotes'.",
      receivedAt: '2026-11-05T14:00:00Z',
    },
    {
      id: 6,
      source: 'JSON',
      payload: '{"externalRef": "GP-TEST-BAD-6"',
      error: 'Malformed JSON',
      receivedAt: '2026-11-04T14:00:00Z',
    },
  ]
}

export const db = {
  referrals: initialReferrals(),
  deadLetters: initialDeadLetters(),
  importedRefs: new Map<string, number>(),
  nextReferralId: 500,
  nextDeadLetterId: 77,
}

export function resetDb() {
  db.referrals = initialReferrals()
  db.deadLetters = initialDeadLetters()
  db.importedRefs = new Map()
  db.nextReferralId = 500
  db.nextDeadLetterId = 77
}

export function findReferral(id: number): ReferralDetail | undefined {
  return db.referrals.find((r) => r.id === id)
}
