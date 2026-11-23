import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'
import type { ReferralFilters } from '../../api/types'

export const REFERRALS_PAGE_SIZE = 20

/**
 * One page of referrals matching the filters. The whole filter object is the query key,
 * so every combination of status/clinic/priority/page/sort is cached separately.
 */
export function useReferrals(filters: ReferralFilters) {
  return useQuery({
    queryKey: ['referrals', filters],
    queryFn: ({ signal }) => api.getReferrals(filters, signal),
    placeholderData: keepPreviousData,
  })
}
