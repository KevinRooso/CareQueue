import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'

export const DEAD_LETTERS_PAGE_SIZE = 20

/** US-3 (ADMIN): messages that failed to import, newest first (the server's order). */
export function useDeadLetters(page: number) {
  return useQuery({
    queryKey: ['dead-letters', page],
    queryFn: ({ signal }) => api.getDeadLetters(page, DEAD_LETTERS_PAGE_SIZE, signal),
    placeholderData: keepPreviousData,
  })
}
