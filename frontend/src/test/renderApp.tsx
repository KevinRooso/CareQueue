import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AppProviders } from '../app/AppProviders'
import { createQueryClient } from '../app/queryClient'
import { routes } from '../app/routes'
import { basicAuthorization, sessionStore } from '../auth/sessionStore'
import { USERS } from './db'

interface RenderAppOptions {
  /** Start already logged in as this demo user; omit to start logged out. */
  as?: 'nurse' | 'manager'
}

/**
 * Renders the real app (same routes and providers as main.tsx) at `path`, with a fresh
 * query cache, so tests exercise the screens the way a user does.
 */
export function renderApp(path: string, { as }: RenderAppOptions = {}) {
  if (as) {
    const { password, me } = USERS[as]
    sessionStore.start({ user: me, authorization: basicAuthorization(as, password) })
  }
  const queryClient = createQueryClient({ queries: { retry: false } })
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  const user = userEvent.setup()
  const view = render(
    <AppProviders queryClient={queryClient}>
      <RouterProvider router={router} />
    </AppProviders>,
  )
  return { ...view, user, router, queryClient }
}
