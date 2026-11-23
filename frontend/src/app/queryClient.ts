import { QueryClient, type DefaultOptions } from '@tanstack/react-query'
import { ApiError } from '../api/client'

/** Retrying a 4xx never helps (bad input, no permission, conflict); retry network/5xx twice. */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < 2
}

export function createQueryClient(overrides: DefaultOptions = {}): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: shouldRetry,
        ...overrides.queries,
      },
      mutations: {
        // Commands are version-guarded; never resend one automatically.
        retry: false,
        ...overrides.mutations,
      },
    },
  })
}
