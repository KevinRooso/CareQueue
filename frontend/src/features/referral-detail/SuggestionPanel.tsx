import { isApiError } from '../../api/client'
import type { ReferralDetail } from '../../api/types'
import { ErrorBanner } from '../../components/ErrorBanner'
import { formatConfidence, PRIORITY_LABEL, SUGGESTION_SOURCE_LABEL } from '../../components/format'
import { PriorityBadge } from '../../components/PriorityBadge'
import { Spinner } from '../../components/Spinner'
import ui from '../../components/ui.module.css'
import { CONFLICT_MESSAGE, useRequestSuggestion } from './hooks'
import type { NoticeHandler } from './notice'
import styles from './ReferralDetailPage.module.css'

interface SuggestionPanelProps {
  referral: ReferralDetail
  canSuggest: boolean
  onNotice: NoticeHandler
}

/**
 * US-4: the AI's advisory suggestion. Drawn with a dashed outline, like the suggested
 * priority tag, because nothing in it is applied until a clinician confirms.
 */
export function SuggestionPanel({ referral, canSuggest, onNotice }: SuggestionPanelProps) {
  const suggest = useRequestSuggestion(referral.id, {
    onSuccess: (detail) => {
      const priority = detail.suggestedPriority
        ? PRIORITY_LABEL[detail.suggestedPriority]
        : 'no priority'
      const source =
        detail.suggestionSource === 'RULE_BASED_FALLBACK' ? ' from the rule-based fallback' : ''
      onNotice({
        tone: 'success',
        text: `Suggestion ready${source}: ${priority}. Review it, then confirm a priority.`,
      })
    },
    onError: (error) => {
      if (isApiError(error, 409)) onNotice({ tone: 'warning', text: CONFLICT_MESSAGE })
    },
  })
  const hasSuggestion = referral.suggestedPriority !== null
  const isFallback = referral.suggestionSource === 'RULE_BASED_FALLBACK'

  function requestSuggestion() {
    onNotice(null)
    suggest.mutate()
  }

  return (
    <section className={styles.suggestion} aria-labelledby="suggestion-heading" aria-busy={suggest.isPending}>
      <div className={styles.panelHeader}>
        <h2 id="suggestion-heading">AI suggestion</h2>
        {hasSuggestion && referral.suggestionSource && (
          <span className={styles.source} data-source={referral.suggestionSource}>
            {SUGGESTION_SOURCE_LABEL[referral.suggestionSource]}
          </span>
        )}
      </div>

      <p className={styles.advisory}>
        Advisory only. The suggestion never sets the priority: you decide below.
      </p>

      {hasSuggestion ? (
        <div className={styles.suggestionBody}>
          {isFallback && (
            <p className={styles.fallbackNote}>
              The AI model was unavailable or gave an unusable answer, so keyword rules produced
              this suggestion. Read the clinical notes before you decide.
            </p>
          )}
          <dl className={styles.facts}>
            <div>
              <dt>Suggested priority</dt>
              <dd>
                <PriorityBadge priority={referral.suggestedPriority!} variant="suggested" size="lg" />
              </dd>
            </div>
            {referral.suggestionConfidence !== null && (
              <div>
                <dt>Confidence</dt>
                <dd className="numeric">{formatConfidence(referral.suggestionConfidence)}</dd>
              </div>
            )}
            {referral.promptVersion && (
              <div>
                <dt>Prompt version</dt>
                <dd>{referral.promptVersion}</dd>
              </div>
            )}
          </dl>

          {referral.aiSummary && (
            <div className={styles.block}>
              <h3>Summary</h3>
              <p>{referral.aiSummary}</p>
            </div>
          )}

          <div className={styles.block}>
            <h3>Red flags</h3>
            {referral.redFlags && referral.redFlags.length > 0 ? (
              <ul className={styles.redFlags}>
                {referral.redFlags.map((flag) => (
                  <li key={flag}>{flag}</li>
                ))}
              </ul>
            ) : (
              <p className={styles.muted}>None found in the notes.</p>
            )}
          </div>
        </div>
      ) : (
        <p className={styles.muted}>
          {canSuggest
            ? 'No suggestion yet. The AI reads the clinical notes and suggests a priority with any red flags.'
            : 'No AI suggestion was recorded for this referral.'}
        </p>
      )}

      {canSuggest && (
        <div className={ui.actions}>
          <button
            type="button"
            className={hasSuggestion ? ui.secondary : ui.primary}
            onClick={requestSuggestion}
            disabled={suggest.isPending}
          >
            {suggest.isPending
              ? 'Getting suggestion…'
              : hasSuggestion
                ? 'Get a new AI suggestion'
                : 'Get AI suggestion'}
          </button>
        </div>
      )}
      {suggest.isPending && (
        <Spinner label="Reading the referral. This can take a few seconds" />
      )}
      {suggest.isError && !isApiError(suggest.error, 409) && (
        <ErrorBanner error={suggest.error} title="Could not get a suggestion" />
      )}
    </section>
  )
}
