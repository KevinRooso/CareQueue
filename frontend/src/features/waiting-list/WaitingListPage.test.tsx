import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { Page, WaitingListEntry } from '../../api/types'
import { api } from '../../test/handlers'
import { renderApp } from '../../test/renderApp'
import { server } from '../../test/server'

function rowsOf(tableName: RegExp) {
  const table = screen.getByRole('table', { name: tableName })
  // Skip the header row.
  return within(table).getAllByRole('row').slice(1)
}

describe('waiting list', () => {
  it('shows the rows in the order the API returns them, with priority as text', async () => {
    // Deliberately not in date order: the UI must keep the server's order, not re-sort.
    const entries: WaitingListEntry[] = [
      {
        id: 3,
        patient: { mrn: 'MRN-900013', firstName: 'Jamie', lastName: 'Placeholder' },
        confirmedPriority: 'URGENT',
        priorityRank: 1,
        receivedAt: '2026-11-10T09:00:00Z',
        triagedBy: 'nurse',
      },
      {
        id: 1,
        patient: { mrn: 'MRN-900011', firstName: 'Alex', lastName: 'Sample' },
        confirmedPriority: 'SOON',
        priorityRank: 2,
        receivedAt: '2026-10-01T09:00:00Z',
        triagedBy: 'nurse',
      },
      {
        id: 2,
        patient: { mrn: 'MRN-900012', firstName: 'Sam', lastName: 'Test' },
        confirmedPriority: 'ROUTINE',
        priorityRank: 3,
        receivedAt: '2026-09-01T09:00:00Z',
        triagedBy: 'manager',
      },
    ]
    server.use(
      http.get(api('/waiting-list'), () =>
        HttpResponse.json<Page<WaitingListEntry>>({
          content: entries,
          page: { size: 20, number: 0, totalElements: 3, totalPages: 1 },
        }),
      ),
    )

    renderApp('/waiting-list', { as: 'nurse' })

    expect(await screen.findByRole('table', { name: /Gastroenterology: 3 patients waiting/ })).toBeInTheDocument()
    const rows = rowsOf(/Gastroenterology/)
    expect(rows.map((row) => row.textContent)).toEqual([
      expect.stringMatching(/^1Urgent.*Jamie Placeholder.*MRN-900013/),
      expect.stringMatching(/^2Soon.*Alex Sample.*MRN-900011/),
      expect.stringMatching(/^3Routine.*Sam Test.*MRN-900012/),
    ])
    expect(within(rows[0]).getByRole('link', { name: 'Jamie Placeholder' })).toHaveAttribute(
      'href',
      '/referrals/3',
    )
  })

  it('defaults to the first clinic and refetches when another clinic is chosen', async () => {
    const { user, router } = renderApp('/waiting-list', { as: 'nurse' })

    const clinic = await screen.findByLabelText('Clinic')
    expect(clinic).toHaveValue('1')
    expect(await screen.findByRole('link', { name: 'Jamie Placeholder' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Robin Example' })).toBeInTheDocument()

    await user.selectOptions(clinic, 'Dermatology')

    expect(await screen.findByRole('link', { name: 'Casey Demo' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Jamie Placeholder' })).not.toBeInTheDocument()
    expect(router.state.location.search).toBe('?clinicId=2')
  })

  it('reads the clinic from the URL and explains an empty list', async () => {
    renderApp('/waiting-list?clinicId=3', { as: 'nurse' })

    expect(await screen.findByText('Nobody is waiting for Cardiology')).toBeInTheDocument()
    expect(screen.getByLabelText('Clinic')).toHaveValue('3')
  })
})
