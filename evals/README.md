# AI triage evals

Evals measure how good the AI triage suggestion is, so prompt or model changes are decided by numbers, not by feel.
All cases are **synthetic**.

## Dataset
`triage/dataset.jsonl` – one JSON object per line:

| Field | Meaning |
|---|---|
| `id` | case id |
| `clinic`, `age`, `clinicalNotes` | input sent to the model |
| `expectedPriority` | what an experienced triage nurse would choose |
| `mustMention` | key facts the summary or red flags should contain (case-insensitive substring match; `a\|b` means either is fine) |
| `mustNotMention` | facts that are **not in the letter at all** (not even negated); if they appear, the model invented them |

The 10 cases cover 3 clinics and all 3 priorities (4 URGENT, 3 SOON, 3 ROUTINE). Add more cases over time, especially tricky ones near the URGENT/SOON boundary.

## Runner
`backend/src/test/java/com/carequeue/triage/eval/TriageEvalTest.java`, tagged `@Tag("eval")` so it is skipped in normal builds.
Run: `cd backend && ./mvnw -q test -Dgroups=eval` (needs `GROQ_API_KEY`; model from `CAREQUEUE_TRIAGE_MODEL`).
It calls the configured `TriageAiClient` — Groq through Spring AI's OpenAI-compatible starter — for each case and writes `evals/results/<date>-<promptVersion>.md`.

## Metrics and pass thresholds
| Metric | How it's calculated | Pass |
|---|---|---|
| **Urgent recall** | of the cases expected URGENT, % the model marked URGENT | **100%** (hard fail otherwise) |
| Priority accuracy | % of cases where suggested = expected | ≥ 80% |
| Schema-valid rate | % of responses that parsed into `TriageSuggestion` without fallback | 100% |
| Red-flag recall | % of `mustMention` facts found in summary or red flags | ≥ 80% |
| Unsupported claims | count of `mustNotMention` facts that appeared | 0 |
| Latency p50 / p95 | per call | report only |

## Why urgent recall is the key metric
Over-prioritising wastes a slot; under-prioritising an urgent case can harm a patient. So the prompt tells the model "when in doubt, choose the more urgent level", and the eval fails the build if any urgent case is missed.

## Report format (generated)
```
# Triage eval – triage-v1 – 2026-11-23
Client: SpringAiTriageAiClient (model: ...)
| Metric | Value | Pass |
| Urgent recall | 4/4 (100%) | ✅ |
| Priority accuracy | 9/10 (90%) | ✅ |
...
## Failures
- T07: expected SOON, got URGENT – "model treated stable palpitations as exertional"
```
