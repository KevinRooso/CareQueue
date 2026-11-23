import { useState } from 'react'
import { isApiError } from '../../api/client'
import type { ReferralDetail } from '../../api/types'
import { ErrorBanner } from '../../components/ErrorBanner'
import ui from '../../components/ui.module.css'
import { CONFLICT_MESSAGE, useRejectReferral } from './hooks'
import type { NoticeHandler } from './notice'
import styles from './ReferralDetailPage.module.css'

/** Reject is final, so it takes two steps: ask, then confirm. */
export function RejectAction({
  referral,
  onNotice,
}: {
  referral: ReferralDetail
  onNotice: NoticeHandler
}) {
  const [confirming, setConfirming] = useState(false)
  const reject = useRejectReferral(referral.id, {
    onSuccess: () => {
      setConfirming(false)
      onNotice({ tone: 'success', text: 'Referral rejected. It has left the triage queue.' })
    },
    onError: (error) => {
      if (isApiError(error, 409)) {
        setConfirming(false)
        onNotice({ tone: 'warning', text: CONFLICT_MESSAGE })
      }
    },
  })

  if (!confirming) {
    return (
      <div className={ui.actions}>
        <button
          type="button"
          className={ui.danger}
          onClick={() => {
            reject.reset()
            setConfirming(true)
          }}
        >
          Reject referral
        </button>
      </div>
    )
  }

  return (
    <div className={styles.rejectConfirm} role="group" aria-labelledby="reject-question">
      <p id="reject-question" className={styles.rejectQuestion}>
        Reject this referral? It leaves the triage queue and cannot be undone.
      </p>
      {reject.isError && !isApiError(reject.error, 409) && (
        <ErrorBanner error={reject.error} title="Could not reject the referral" />
      )}
      <div className={ui.actions}>
        <button
          type="button"
          className={ui.dangerSolid}
          disabled={reject.isPending}
          onClick={() => {
            onNotice(null)
            reject.mutate({ version: referral.version })
          }}
        >
          {reject.isPending ? 'Rejecting…' : 'Yes, reject referral'}
        </button>
        {/* Focus lands on the safe choice when the question appears. */}
        <button
          type="button"
          className={ui.secondary}
          autoFocus
          disabled={reject.isPending}
          onClick={() => setConfirming(false)}
        >
          Keep referral
        </button>
      </div>
    </div>
  )
}
