import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { api, isApiError } from '../../api/client'
import type {
  Appointment,
  BookAppointmentRequest,
  ConfirmTriageRequest,
  ReferralDetail,
  RejectReferralRequest,
} from '../../api/types'

export const referralKey = (id: number) => ['referral', id] as const

/** Shown after any 409 on suggest, confirm or reject. */
export const CONFLICT_MESSAGE =
  'This referral changed since you opened it. The latest version has been loaded.'

export function useReferral(id: number) {
  return useQuery({
    queryKey: referralKey(id),
    queryFn: ({ signal }) => api.getReferral(id, signal),
  })
}

/**
 * Callbacks for the component that runs a command. They are passed to `useMutation` itself
 * (not to `mutate()`), because TanStack Query skips `mutate()` callbacks when the component
 * has unmounted, and a successful confirm unmounts the confirm form.
 */
export interface CommandCallbacks<TData> {
  onSuccess?: (data: TData) => void
  onError?: (error: Error) => void
}

/** Lists that show this referral (inbox, waiting lists) are now out of date. */
function invalidateLists(queryClient: QueryClient) {
  void queryClient.invalidateQueries({ queryKey: ['referrals'] })
  void queryClient.invalidateQueries({ queryKey: ['waiting-list'] })
}

/**
 * Shared wiring for commands that return the updated referral:
 * - success: the response *is* the new detail, so write it straight into the cache
 *   (no extra GET) and mark the lists stale;
 * - 409: someone else changed the referral (or its status moved on), so reload it.
 */
function useReferralCommand<TVariables>(
  id: number,
  command: (variables: TVariables) => Promise<ReferralDetail>,
  callbacks: CommandCallbacks<ReferralDetail> = {},
) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: command,
    onSuccess: (detail) => {
      queryClient.setQueryData(referralKey(id), detail)
      invalidateLists(queryClient)
      callbacks.onSuccess?.(detail)
    },
    onError: (error) => {
      if (isApiError(error, 409)) void queryClient.invalidateQueries({ queryKey: referralKey(id) })
      callbacks.onError?.(error)
    },
  })
}

/** US-4: ask the AI (or the rule-based fallback) for a suggestion. Can take a few seconds. */
export function useRequestSuggestion(id: number, callbacks?: CommandCallbacks<ReferralDetail>) {
  return useReferralCommand<void>(id, () => api.requestTriageSuggestion(id), callbacks)
}

/** US-5/US-6: the clinician's decision, guarded by the version they loaded. */
export function useConfirmTriage(id: number, callbacks?: CommandCallbacks<ReferralDetail>) {
  return useReferralCommand<ConfirmTriageRequest>(
    id,
    (body) => api.confirmTriage(id, body),
    callbacks,
  )
}

export function useRejectReferral(id: number, callbacks?: CommandCallbacks<ReferralDetail>) {
  return useReferralCommand<RejectReferralRequest>(
    id,
    (body) => api.rejectReferral(id, body),
    callbacks,
  )
}

/** US-8 (ADMIN). Returns the appointment, not the referral, so the referral is refetched. */
export function useBookAppointment(id: number, callbacks: CommandCallbacks<Appointment> = {}) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: BookAppointmentRequest) => api.bookAppointment(id, body),
    onSuccess: (appointment) => {
      // The status is now BOOKED: reload the referral and the lists it appears in.
      void queryClient.invalidateQueries({ queryKey: referralKey(id) })
      invalidateLists(queryClient)
      callbacks.onSuccess?.(appointment)
    },
    onError: (error) => {
      if (isApiError(error, 409)) void queryClient.invalidateQueries({ queryKey: referralKey(id) })
      callbacks.onError?.(error)
    },
  })
}
