import { useQuery } from '@tanstack/react-query'
import { api } from '../../api/client'

/** All clinics. Reference data that rarely changes, so it stays fresh for 10 minutes. */
export function useClinics() {
  return useQuery({
    queryKey: ['clinics'],
    queryFn: ({ signal }) => api.getClinics(signal),
    staleTime: 10 * 60_000,
  })
}
