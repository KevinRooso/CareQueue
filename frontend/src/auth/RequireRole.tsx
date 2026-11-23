import type { ReactNode } from 'react'
import type { Role } from '../api/types'
import { NotAllowedPage } from '../app/NotAllowedPage'
import { useHasRole } from './useAuth'

/** Route guard for role-restricted pages. The server returns 403 for these endpoints too. */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  return useHasRole(role) ? children : <NotAllowedPage />
}
