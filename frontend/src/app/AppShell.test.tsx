import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/renderApp'

describe('app shell and roles', () => {
  it('hides the admin pages from a clinician', async () => {
    renderApp('/waiting-list', { as: 'nurse' })
    await screen.findByRole('heading', { level: 1, name: 'Waiting list' })

    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'Waiting list' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Referral inbox' })).toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Import' })).not.toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Dead letters' })).not.toBeInTheDocument()
    expect(screen.getByText('Synthetic data – demo')).toBeInTheDocument()
  })

  it('shows "Not allowed" when a clinician opens an admin page directly', async () => {
    renderApp('/import', { as: 'nurse' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Not allowed' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Referral message')).not.toBeInTheDocument()
  })

  it('shows the admin pages to a manager', async () => {
    renderApp('/waiting-list', { as: 'manager' })
    await screen.findByRole('heading', { level: 1, name: 'Waiting list' })

    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'Import' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Dead letters' })).toBeInTheDocument()
    expect(screen.getByText(/Signed in as/).closest('p')).toHaveTextContent('manager Admin')
  })

  it('redirects / to the waiting list and shows a not-found page for unknown addresses', async () => {
    const { router } = renderApp('/', { as: 'nurse' })
    expect(await screen.findByRole('heading', { level: 1, name: 'Waiting list' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/waiting-list')

    await router.navigate('/no-such-page')
    expect(await screen.findByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument()
  })
})
