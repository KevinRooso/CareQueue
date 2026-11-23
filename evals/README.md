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
Run: `cd backend && ./mvnw -q test -Dgroups=eval`.

- The key comes from `GROQ_API_KEY` (environment, or the repo-root `.env`, which the app imports when run from `backend/`). The model comes from `CAREQUEUE_TRIAGE_MODEL` (default `llama-3.3-70b-versatile`).
- It starts only the triage part of the app (`TriageSliceConfig`: real `application.yml`, Spring AI, Jackson, `TriageConfig`), so it needs no database and no Docker.
- It calls the `@Primary` `TriageAiClient` (`ResilientTriageAiClient`, which calls Groq through Spring AI's OpenAI-compatible client and falls back to the rules) once per case.
- It is the **only** test allowed to call the model. All other tests run with a blank key (`backend/src/test/resources/config/application.yml`).

### Report file
`evals/results/<yyyy-MM-dd>-<promptVersion>-<model-slug>.md`, for example `2026-11-23-triage-v1-llama-3-3-70b-versatile.md`.
The model slug is the model name in lower case with every run of other characters replaced by `-`. Running twice on the same day overwrites the file.

### Fallback mode (no model answered)
If no model answered, the file name ends in `-fallback.md` (e.g. `2026-11-23-triage-v1-fallback.md`) and the report starts with a **FALLBACK MODE** banner: it is **not a model evaluation**, it describes the rule-based safety net (`rules-v1`). There are two cases:
- **No key** (`GROQ_API_KEY` blank or missing): no HTTP call is made at all.
- **Key set but every call fell back** (bad key, wrong model name, rate limit, network): the banner says the model never answered.

In fallback mode the thresholds are **not enforced** and the test passes; the Pass column only shows what they would say. Thresholds are asserted only when a real model answered at least one case (any result with source `AI`).

## Metrics and pass thresholds
| Metric | How it's calculated | Pass |
|---|---|---|
| **Urgent recall** | of the cases expected URGENT, % the model marked URGENT | **100%** (hard fail otherwise) |
| Priority accuracy | % of cases where suggested = expected | ≥ 80% |
| Schema-valid rate | % of responses with source `AI` (the reply parsed and passed validation, no fallback) | 100% |
| Red-flag recall | % of `mustMention` facts found in summary or red flags | ≥ 80% |
| Unsupported claims | count of `mustNotMention` facts that appeared in summary or red flags | 0 |
| Latency p50 / p95 | per call, nearest-rank percentile | report only |

## Why urgent recall is the key metric
Over-prioritising wastes a slot; under-prioritising an urgent case can harm a patient. So the prompt tells the model "when in doubt, choose the more urgent level", and the eval fails the build if any urgent case is missed.

## Report format (generated)
```
# Triage eval – triage-v1 – 2026-11-23

- Client: SpringAiTriageAiClient via ResilientTriageAiClient (model: llama-3.3-70b-versatile, base URL: https://api.groq.com/openai/v1)
- Prompt version: triage-v1
- Dataset: evals/triage/dataset.jsonl (10 cases)

| Metric | Value | Pass |
|---|---|---|
| Urgent recall | 4/4 (100%) | ✅ |
| Priority accuracy | 9/10 (90%) | ✅ |
...

## Per case
| Case | Expected | Got | Source | Red flags | Latency |
...

## Failures
- T07: expected SOON, got URGENT (source AI)
```
Failure lines are generated (wrong priority, `missing "<mustMention>"`, `unsupported "<mustNotMention>"`). Add a one-line human reason when you review the report, e.g. `– model treated stable palpitations as exertional`.
