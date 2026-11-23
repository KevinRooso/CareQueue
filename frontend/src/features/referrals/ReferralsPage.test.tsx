import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../../test/renderApp'

describe('referral inbox', () => {
  it('shows referrals that need triage by default, split into ready-to-confirm and new', async () => {
    renderApp('/referrals', { as: 'nurse' })

    expect(screen.getByLabelText('Status')).toHaveValue('needs-triage')
    const ready = await screen.findByRole('region', { name: 'Ready to confirm' })
    const waiting = screen.getByRole('region', { name: 'Waiting for an AI suggestion' })

    const alex = await within(ready).findByRole('row', { name: /Alex Sample/ })
    expect(within(alex).getByText('AI suggested')).toBeInTheDocument()
    expect(within(alex).getByText('Urgent')).toBeInTheDocument()
    expect(await within(waiting).findByRole('link', { name: 'Sam Test' })).toBeInTheDocument()
    // Triaged referrals are not in the triage inbox.
    expect(screen.queryByRole('link', { name: 'Jamie Placeholder' })).not.toBeInTheDocument()
  })

  it('filters by status', async () => {
    const { user, router } = renderApp('/referrals', { as: 'nurse' })
    await screen.findByRole('link', { name: 'Alex Sample' })

    await user.selectOptions(screen.getByLabelText('Status'), 'Triaged')

    const table = await screen.findByRole('table', { name: /Triaged: 3 referrals/ })
    expect(within(table).getByRole('link', { name: 'Jamie Placeholder' })).toBeInTheDocument()
    expect(within(table).getByRole('link', { name: 'Casey Demo' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Alex Sample' })).not.toBeInTheDocument()
    expect(router.state.location.search).toBe('?status=TRIAGED')
  })

  it('combines status, clinic and priority filters from the URL', async () => {
    renderApp('/referrals?status=TRIAGED&clinicId=1&priority=URGENT', { as: 'nurse' })

    const table = await screen.findByRole('table', { name: /Triaged: 1 referral,/ })
    expect(within(table).getByRole('link', { name: 'Jamie Placeholder' })).toBeInTheDocument()
    expect(screen.getByLabelText('Confirmed priority')).toHaveValue('URGENT')
  })
})
