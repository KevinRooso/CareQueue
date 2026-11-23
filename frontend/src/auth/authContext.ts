import { createContext } from 'react'
import type { CurrentUser } from '../api/types'

export interface AuthContextValue {
  /** null when nobody is logged in */
  user: CurrentUser | null
  /** true when the last session ended because the server answered 401 */
  expired: boolean
  /** Verifies the credentials with GET /api/me and starts the session; throws ApiError on failure. */
  login: (username: string, password: string) => Promise<CurrentUser>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
