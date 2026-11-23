import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type ImportContentType } from '../../api/client'

/** XML always starts with `<` (a declaration or the root element); anything else is sent as JSON. */
export function detectContentType(message: string): ImportContentType {
  return message.trimStart().startsWith('<') ? 'application/xml' : 'application/json'
}

/**
 * US-1/2/3 (ADMIN): send one message to the import endpoint. A 422 rejects the message and
 * stores it as a dead letter, so the dead-letter list is stale after a failure; a success
 * adds a referral to the inbox.
 */
export function useImportReferral() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (message: string) => api.importReferral(message, detectContentType(message)),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['referrals'] }),
    onError: () => void queryClient.invalidateQueries({ queryKey: ['dead-letters'] }),
  })
}
