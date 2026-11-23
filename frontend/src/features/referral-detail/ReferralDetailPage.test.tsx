import { fireEvent, screen, within } from '@testing-library/react'
import { delay, http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { ReferralDetail } from '../../api/types'
import { db, findReferral } from '../../test/db'
import { api } from '../../test/handlers'
import { renderApp } from '../../test/renderApp'
import { server } from '../../test/server'

const CONFLICT = 'This referral changed since you opened it. The latest version has been loaded.'

function triageColumn() {
  return screen.getByRole('region', { name: 'AI suggestion' })
}

describe('referral detail', () => {
  it('shows the patient, the referral and the clinical notes', async () => {
    renderApp('/referrals/102', { as: 'nurse' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Sam Test' })).toBeInTheDocument()
    expect(screen.getByText('MRN-900002')).toBeInTheDocument()
    expect(screen.getByText('Referral to Dermatology')).toBeInTheDocument()
    expect(screen.getByText(/Long-standing mild eczema/)).toBeInTheDocument()
    expect(screen.getByText('Received', { selector: 'span' })).toBeInTheDocument()
  })

  it('gets an AI suggestion, shows progress, then the summary, red flags and the fallback label', async () => {
    server.use(
      http.post(api('/referrals/:id/triage-suggestion'), async () => {
        await delay(50)
        const updated: ReferralDetail = {
          ...findReferral(102)!,
          status: 'AI_SUGGESTED',
          aiSummary: 'Rectal bleeding with weight loss in a 60-year-old.',
          suggestedPriority: 'URGENT',
          suggestionSource: 'RULE_BASED_FALLBACK',
          suggestionConfidence: 0.5,
          promptVersion: 'rules-v1',
          redFlags: ['rectal bleeding', 'weight loss'],
          version: 1,
        }
        return HttpResponse.json(updated)
      }),
    )
    const { user } = renderApp('/referrals/102', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Sam Test' })
    expect(within(triageColumn()).getByText(/No suggestion yet/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Get AI suggestion' }))

    expect(screen.getByRole('button', { name: 'Getting suggestion…' })).toBeDisabled()
    expect(screen.getByText(/Reading the referral/)).toBeInTheDocument()

    expect(await screen.findByText('Rectal bleeding with weight loss in a 60-year-old.')).toBeInTheDocument()
    const panel = triageColumn()
    expect(within(panel).getByText('Rule-based fallback')).toBeInTheDocument()
    expect(within(panel).getByText(/keyword rules produced this suggestion/)).toBeInTheDocument()
    expect(within(panel).getByText('rectal bleeding')).toBeInTheDocument()
    expect(within(panel).getByText('weight loss')).toBeInTheDocument()
    expect(within(panel).getByText('50%')).toBeInTheDocument()
    expect(within(panel).getByText(/Advisory only/)).toBeInTheDocument()
    // The result is announced in the page's live region.
    expect(screen.getByText(/Suggestion ready from the rule-based fallback: Urgent/).parentElement).toHaveAttribute(
      'aria-live',
      'polite',
    )
    // The suggestion is preselected in the confirm form, but nothing has been confirmed.
    expect(screen.getByRole('radio', { name: /Urgent/ })).toBeChecked()
    expect(screen.getByText('AI suggested', { selector: 'span' })).toBeInTheDocument()
  })

  it('confirms the suggested priority and the status becomes Triaged', async () => {
    const { user } = renderApp('/referrals/101', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Alex Sample' })
    expect(within(triageColumn()).getByText('AI model')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /Urgent/ })).toBeChecked()

    await user.type(screen.getByLabelText('Note (optional)'), 'Agree with the suggestion.')
    await user.click(screen.getByRole('button', { name: 'Confirm priority' }))

    expect(await screen.findByText(/Priority confirmed: Urgent/)).toBeInTheDocument()
    expect(screen.getByText('Triaged', { selector: 'span' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm priority' })).not.toBeInTheDocument()
    expect(screen.getByText('Agree with the suggestion.')).toBeInTheDocument()
    // The request carried the version that was on screen.
    expect(findReferral(101)).toMatchObject({ status: 'TRIAGED', confirmedPriority: 'URGENT', version: 4 })
  })

  it('lets the clinician change the priority away from the suggestion', async () => {
    const { user } = renderApp('/referrals/101', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Alex Sample' })

    await user.click(screen.getByRole('radio', { name: /Soon/ }))
    expect(screen.getByText(/changing the AI suggestion from Urgent to Soon/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Confirm priority' }))

    expect(await screen.findByText(/Priority confirmed: Soon/)).toBeInTheDocument()
    expect(findReferral(101)?.confirmedPriority).toBe('SOON')
  })

  it('shows the "changed" message and reloads the referral when confirm gets a 409', async () => {
    const { user } = renderApp('/referrals/101', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Alex Sample' })

    // Another clinician asks for a new suggestion while this page is open.
    Object.assign(findReferral(101)!, {
      version: 4,
      suggestedPriority: 'SOON',
      aiSummary: 'Updated summary from a newer suggestion.',
    })
    await user.click(screen.getByRole('button', { name: 'Confirm priority' }))

    expect(await screen.findByText(CONFLICT)).toBeInTheDocument()
    expect(await screen.findByText('Updated summary from a newer suggestion.')).toBeInTheDocument()
    // The form starts again from the latest version, with the new suggestion preselected.
    expect(screen.getByRole('radio', { name: /Soon/ })).toBeChecked()
    expect(findReferral(101)?.status).toBe('AI_SUGGESTED')
  })

  it('shows the "changed" message when reject gets a 409', async () => {
    const { user } = renderApp('/referrals/101', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Alex Sample' })
    findReferral(101)!.version = 9

    await user.click(screen.getByRole('button', { name: 'Reject referral' }))
    await user.click(screen.getByRole('button', { name: 'Yes, reject referral' }))

    expect(await screen.findByText(CONFLICT)).toBeInTheDocument()
  })

  it('rejects only after a confirmation step', async () => {
    const { user } = renderApp('/referrals/102', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Sam Test' })

    await user.click(screen.getByRole('button', { name: 'Reject referral' }))
    expect(screen.getByText(/cannot be undone/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Keep referral' })).toHaveFocus()

    await user.click(screen.getByRole('button', { name: 'Keep referral' }))
    expect(findReferral(102)?.status).toBe('RECEIVED')

    await user.click(screen.getByRole('button', { name: 'Reject referral' }))
    await user.click(screen.getByRole('button', { name: 'Yes, reject referral' }))

    expect(await screen.findByText(/Referral rejected/)).toBeInTheDocument()
    expect(screen.getByText('Rejected', { selector: 'span' })).toBeInTheDocument()
    expect(findReferral(102)?.status).toBe('REJECTED')
  })

  it('lets a manager book an appointment for a triaged referral', async () => {
    const { user } = renderApp('/referrals/103', { as: 'manager' })
    await screen.findByRole('heading', { level: 1, name: 'Jamie Placeholder' })

    fireEvent.change(screen.getByLabelText('Appointment date and time'), {
      target: { value: '2030-03-01T09:00' },
    })
    await user.click(screen.getByRole('button', { name: 'Book appointment' }))

    expect(await screen.findByText(/Appointment booked for 1 Mar 2030/)).toBeInTheDocument()
    expect(await screen.findByText('Booked', { selector: 'span' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Book appointment' })).not.toBeInTheDocument()
    expect(findReferral(103)?.status).toBe('BOOKED')
  })

  it('shows the server field error next to the date when booking in the past', async () => {
    const { user } = renderApp('/referrals/103', { as: 'manager' })
    await screen.findByRole('heading', { level: 1, name: 'Jamie Placeholder' })

    const input = screen.getByLabelText('Appointment date and time')
    fireEvent.change(input, { target: { value: '2020-01-01T09:00' } })
    await user.click(screen.getByRole('button', { name: 'Book appointment' }))

    expect(await screen.findByText('must be a future date')).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription(/must be a future date/)
  })

  it('does not offer booking to a clinician', async () => {
    renderApp('/referrals/103', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Jamie Placeholder' })

    expect(screen.queryByLabelText('Appointment date and time')).not.toBeInTheDocument()
    expect(screen.getByText(/A clinic manager books the appointment/)).toBeInTheDocument()
  })

  it('shows the change history to a manager', async () => {
    renderApp('/referrals/103', { as: 'manager' })
    await screen.findByRole('heading', { level: 1, name: 'Jamie Placeholder' })

    const history = await screen.findByRole('region', { name: 'History' })
    const rows = within(history).getAllByRole('listitem')
    expect(rows.length).toBeGreaterThan(0)
    expect(within(history).getByText('system')).toBeInTheDocument()
    expect(within(history).getByText(/created the referral/)).toBeInTheDocument()
    expect(within(history).getAllByText(/Status/).length).toBeGreaterThan(0)
    expect(within(history).getByText(/Confirmed priority/)).toBeInTheDocument()
    expect(within(history).getAllByRole('time').length).toBeGreaterThan(0)
  })

  it('hides the change history from a clinician', async () => {
    renderApp('/referrals/103', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Jamie Placeholder' })

    expect(screen.queryByRole('region', { name: 'History' })).not.toBeInTheDocument()
  })

  it('explains a referral that does not exist', async () => {
    db.referrals = db.referrals.filter((r) => r.id !== 101)
    renderApp('/referrals/101', { as: 'nurse' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Referral not found' })).toBeInTheDocument()
  })
})
