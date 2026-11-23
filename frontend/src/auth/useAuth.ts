import { use } from 'react'
import type { Role } from '../api/types'
import { AuthContext, type AuthContextValue } from './authContext'

export function useAuth(): AuthContextValue {
  const value = use(AuthContext)
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>')
  return value
}

/**
 * Does the logged-in user have this role? `/api/me` already returns effective roles
 * (ADMIN implies CLINICIAN), so this is a plain lookup. It only hides UI; the server
 * enforces every role check itself.
 */
export function useHasRole(role: Role): boolean {
  const { user } = useAuth()
  return user?.roles.includes(role) ?? false
}
