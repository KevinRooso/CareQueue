import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from './useAuth'

export interface LoginLocationState {
  /** Where to go back to after logging in again (only set when a session expired). */
  from?: string
}

/** Route guard: anyone without a session is sent to /login. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, expired } = useAuth()
  const location = useLocation()

  if (!user) {
    const state: LoginLocationState = expired
      ? { from: `${location.pathname}${location.search}` }
      : {}
    return <Navigate to="/login" replace state={state} />
  }
  return children
}
