import { screen } from '@testing-library/react'
import { http } from 'msw'
import { describe, expect, it } from 'vitest'
import { api, problem } from '../test/handlers'
import { renderApp } from '../test/renderApp'
import { server } from '../test/server'

describe('logging in', () => {
  it('verifies the credentials with the server and opens the waiting list', async () => {
    const { user } = renderApp('/login')

    await user.type(screen.getByLabelText('Username'), 'nurse')
    await user.type(screen.getByLabelText('Password'), 'nurse-test-only')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Waiting list' })).toBeInTheDocument()
    expect(screen.getByText(/Signed in as/).closest('p')).toHaveTextContent(
      'Signed in as nurse Clinician',
    )
  })

  it('shows an inline error for a wrong password and stays on the login page', async () => {
    const { user } = renderApp('/login')

    await user.type(screen.getByLabelText('Username'), 'nurse')
    await user.type(screen.getByLabelText('Password'), 'not-the-password')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong username or password')
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('route guard', () => {
  it('sends a visitor without a session to the login page', async () => {
    renderApp('/referrals')

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Referral inbox' })).not.toBeInTheDocument()
  })

  it('returns to login when the server answers 401 mid-session, then back after logging in', async () => {
    const { user, router } = renderApp('/waiting-list', { as: 'nurse' })
    expect(await screen.findByRole('heading', { level: 1, name: 'Waiting list' })).toBeInTheDocument()

    // The server stops accepting the stored credentials (e.g. the password was rotated).
    server.use(
      http.get(api('/referrals'), () => problem(401, 'Unauthorized', 'Bad credentials'), {
        once: true,
      }),
    )
    await user.click(screen.getByRole('link', { name: 'Referral inbox' }))

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByText('Your session has ended. Log in again to continue.')).toBeInTheDocument()

    await user.type(screen.getByLabelText('Username'), 'nurse')
    await user.type(screen.getByLabelText('Password'), 'nurse-test-only')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Referral inbox' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/referrals')
  })

  it('logs out and forgets the session', async () => {
    const { user } = renderApp('/waiting-list', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Waiting list' })

    await user.click(screen.getByRole('button', { name: 'Log out' }))

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.queryByText('Your session has ended. Log in again to continue.')).not.toBeInTheDocument()
  })
})
