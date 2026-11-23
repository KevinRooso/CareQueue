import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it } from 'vitest'
import { api } from '../../test/handlers'
import { renderApp } from '../../test/renderApp'
import { server } from '../../test/server'

/** Records the Content-Type the page sends to the import endpoint. */
function captureImportContentType() {
  const captured: { value: string | null } = { value: null }
  server.events.on('request:start', ({ request }) => {
    if (request.url.endsWith('/api/referrals/import')) {
      captured.value = request.headers.get('Content-Type')
    }
  })
  return captured
}

describe('import page', () => {
  afterEach(() => server.events.removeAllListeners())

  it('imports the sample JSON and links to the new referral (201)', async () => {
    const contentType = captureImportContentType()
    const { user } = renderApp('/import', { as: 'manager' })

    await user.click(await screen.findByRole('button', { name: 'Load sample JSON' }))
    expect((screen.getByLabelText('Referral message') as HTMLTextAreaElement).value).toContain(
      '"externalRef": "GP-UI-SAMPLE-001"',
    )
    expect(screen.getByText('Will be sent as JSON.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Import referral' }))

    expect(await screen.findByRole('heading', { name: 'Referral imported' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open referral 500' })).toHaveAttribute('href', '/referrals/500')
    expect(contentType.value).toBe('application/json')
  })

  it('sends text starting with < as XML', async () => {
    const contentType = captureImportContentType()
    const { user } = renderApp('/import', { as: 'manager' })

    await user.click(await screen.findByRole('button', { name: 'Load sample XML' }))
    expect(screen.getByText('Will be sent as XML.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Import referral' }))

    expect(await screen.findByRole('heading', { name: 'Referral imported' })).toBeInTheDocument()
    expect(contentType.value).toBe('application/xml')
  })

  it('says when a message was already imported (200) and links to the existing referral', async () => {
    const { user } = renderApp('/import', { as: 'manager' })
    await user.click(await screen.findByRole('button', { name: 'Load sample JSON' }))
    await user.click(screen.getByRole('button', { name: 'Import referral' }))
    await screen.findByRole('heading', { name: 'Referral imported' })

    await user.click(screen.getByRole('button', { name: 'Import referral' }))

    expect(await screen.findByRole('heading', { name: 'Already imported' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open referral 500' })).toHaveAttribute('href', '/referrals/500')
  })

  it('shows the validation errors and the dead letter id for an invalid message (422)', async () => {
    const { user } = renderApp('/import', { as: 'manager' })
    const message = await screen.findByLabelText('Referral message')
    await user.click(message)
    await user.paste('{"externalRef": "GP-UI-BAD-001", "patient": {"mrn": "MRN-900999"}}')

    await user.click(screen.getByRole('button', { name: 'Import referral' }))

    const banner = await screen.findByText('Invalid referral message')
    const error = banner.closest('div')!.parentElement!
    expect(within(error).getByText('The referral message is invalid.')).toBeInTheDocument()
    const items = within(error).getAllByRole('listitem').map((li) => li.textContent)
    expect(items).toEqual(['clinicCode: must not be blank', 'clinicalNotes: must not be blank'])
    expect(within(error).getByText(/Saved as dead letter 77/)).toBeInTheDocument()
    expect(within(error).getByRole('link', { name: 'View dead letters' })).toHaveAttribute('href', '/dead-letters')
  })

  it('shows a 422 without field errors (malformed JSON) and still gives the dead letter id', async () => {
    server.use(
      http.post(api('/referrals/import'), () =>
        HttpResponse.json(
          {
            type: 'about:blank',
            title: 'Invalid referral message',
            status: 422,
            detail: 'Malformed JSON',
            deadLetterId: 81,
          },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const { user } = renderApp('/import', { as: 'manager' })
    await user.click(await screen.findByLabelText('Referral message'))
    await user.paste('{"externalRef": ')
    await user.click(screen.getByRole('button', { name: 'Import referral' }))

    const detail = await screen.findByText('Malformed JSON')
    const error = detail.parentElement!
    expect(within(error).getByText('Invalid referral message')).toBeInTheDocument()
    expect(within(error).getByText(/Saved as dead letter 81/)).toBeInTheDocument()
    expect(within(error).queryByRole('listitem')).not.toBeInTheDocument()
  })

  it('asks for a message before sending anything', async () => {
    const { user } = renderApp('/import', { as: 'manager' })
    await user.click(await screen.findByRole('button', { name: 'Import referral' }))

    expect(screen.getByText('Paste a message or load a sample first.')).toBeInTheDocument()
    expect(screen.getByLabelText('Referral message')).toHaveAttribute('aria-invalid', 'true')
  })
})
