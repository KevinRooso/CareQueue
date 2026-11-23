import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** One MSW server for the whole test run; tests add overrides with `server.use(...)`. */
export const server = setupServer(...handlers)
