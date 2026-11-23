import type { CurrentUser } from '../api/types'

// The logged-in session lives in this module variable and nowhere else.
// Deliberately NOT in localStorage/sessionStorage/cookies: any script on the page can read
// web storage, so a stored Basic credential would be a long-lived password leak. The cost is
// that a page reload logs you out – acceptable for a demo of a clinical tool.

export interface Session {
  user: CurrentUser
  /** The full `Authorization` header value, e.g. `Basic bnVyc2U6...` */
  authorization: string
}

export interface SessionState {
  session: Session | null
  /** Set when the server rejected our credentials mid-session (a 401), cleared on the next login. */
  expired: boolean
}

type Listener = () => void

let state: SessionState = { session: null, expired: false }
const listeners = new Set<Listener>()

function setState(next: SessionState) {
  state = next
  listeners.forEach((listener) => listener())
}

export const sessionStore = {
  /** Stable snapshot for `useSyncExternalStore`: a new object only when something changed. */
  getState(): SessionState {
    return state
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  start(session: Session) {
    setState({ session, expired: false })
  },
  /** User clicked "Log out". */
  logout() {
    setState({ session: null, expired: false })
  },
  /** The API answered 401 to a request that carried our credentials. */
  expire() {
    if (state.session) setState({ session: null, expired: true })
  },
  authorization(): string | null {
    return state.session?.authorization ?? null
  },
}

/** `Basic base64(username:password)`, UTF-8 safe (plain `btoa` throws on non-Latin-1 input). */
export function basicAuthorization(username: string, password: string): string {
  const bytes = new TextEncoder().encode(`${username}:${password}`)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return `Basic ${btoa(binary)}`
}
