import type { ReferralStatus } from '../../api/types'

export interface ReferralActions {
  canSuggest: boolean
  canConfirm: boolean
  canReject: boolean
  canBook: boolean
}

/**
 * Which controls to show for a status. This mirrors `ReferralStatus.ALLOWED` in the back end
 * only to decide what to display; the server re-checks every command and answers 409 if the
 * move is not allowed. Kept in this one place so components never re-derive the rules.
 */
export function actionsFor(status: ReferralStatus): ReferralActions {
  return {
    canSuggest: status === 'RECEIVED' || status === 'AI_SUGGESTED',
    canConfirm: status === 'AI_SUGGESTED',
    canReject: status === 'RECEIVED' || status === 'AI_SUGGESTED' || status === 'TRIAGED',
    canBook: status === 'TRIAGED',
  }
}
