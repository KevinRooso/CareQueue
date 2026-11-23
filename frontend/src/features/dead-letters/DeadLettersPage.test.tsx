import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../../test/renderApp'

describe('dead letters', () => {
  it('lists failed imports newest first, with the error and a payload preview', async () => {
    const { user } = renderApp('/dead-letters', { as: 'manager' })

    const table = await screen.findByRole('table', { name: /Failed imports: 2 messages, newest first/ })
    const [, newest, older] = within(table).getAllByRole('row')
    expect(within(newest).getByRole('rowheader')).toHaveTextContent('7')
    expect(within(newest).getByText('XML')).toBeInTheDocument()
    expect(within(newest).getByText(/Invalid content starting with element/)).toBeInTheDocument()
    expect(within(older).getByText('Malformed JSON')).toBeInTheDocument()
    expect(within(older).getByText('{"externalRef": "GP-TEST-BAD-6"')).toBeInTheDocument()

    // The long XML payload is cut short until expanded.
    const toggle = within(newest).getByRole('button', { name: 'Show full payload' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(within(newest).getByText(/…$/)).toBeInTheDocument()

    await user.click(toggle)

    expect(within(newest).getByRole('button', { name: 'Show less' })).toHaveAttribute('aria-expanded', 'true')
    expect(within(newest).getByText(/<\/referral>$/)).toBeInTheDocument()
  })

  it('is not available to a clinician', async () => {
    renderApp('/dead-letters', { as: 'nurse' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Not allowed' })).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })
})
