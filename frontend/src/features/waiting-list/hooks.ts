import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'

export const WAITING_LIST_PAGE_SIZE = 20

/**
 * One page of a clinic's waiting list. The server owns the order (priority, then date
 * received); the UI never re-sorts it. `clinicId` is undefined until the clinics load.
 */
export function useWaitingList(clinicId: number | undefined, page: number) {
  return useQuery({
    queryKey: ['waiting-list', clinicId, page],
    queryFn: ({ signal }) =>
      api.getWaitingList(clinicId as number, page, WAITING_LIST_PAGE_SIZE, signal),
    enabled: clinicId !== undefined,
    // Keep showing the old page while the next one loads, instead of a blank table.
    placeholderData: keepPreviousData,
  })
}
