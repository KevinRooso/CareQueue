import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import { sessionStore } from '../auth/sessionStore'
import { resetDb } from './db'
import { server } from './server'

// Any request without a handler fails the test, so no test can silently hit a real server.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
beforeEach(() => resetDb())
afterEach(() => {
  cleanup()
  server.resetHandlers()
  // The session lives in a module variable; end it so tests stay independent.
  sessionStore.logout()
})
afterAll(() => server.close())
