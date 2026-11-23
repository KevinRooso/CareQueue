import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react'
import { api } from '../api/client'
import { AuthContext, type AuthContextValue } from './authContext'
import { basicAuthorization, sessionStore } from './sessionStore'

/**
 * Exposes the in-memory session to React. The session itself lives in `sessionStore`
 * (outside React) so the API client can read the credentials and end the session on a 401
 * without needing React; this provider just subscribes to it.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const state = useSyncExternalStore(sessionStore.subscribe, sessionStore.getState)

  // Whenever a session ends (logout or 401), drop all cached server data so the next
  // person to log in on this browser never sees the previous user's patients.
  useEffect(
    () =>
      sessionStore.subscribe(() => {
        if (!sessionStore.getState().session) queryClient.clear()
      }),
    [queryClient],
  )

  const login = useCallback(
    async (username: string, password: string) => {
      const authorization = basicAuthorization(username, password)
      const user = await api.verifyCredentials(authorization)
      queryClient.clear()
      sessionStore.start({ user, authorization })
      return user
    },
    [queryClient],
  )

  const logout = useCallback(() => sessionStore.logout(), [])

  const value = useMemo<AuthContextValue>(
    () => ({ user: state.session?.user ?? null, expired: state.expired, login, logout }),
    [state, login, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
