# Triage eval – triage-v1 – 2026-11-23

> **FALLBACK MODE – this is not a model evaluation.**
> No `GROQ_API_KEY` was configured, so every case was answered by the rule-based client (`rules-v1`) and no model was called. The numbers describe the safety-net rules only.
> Thresholds are not enforced; the Pass column only shows what they would say.
> Set `GROQ_API_KEY` and run again to evaluate the model.

- Client: RuleBasedTriageAiClient via ResilientTriageAiClient (rules-v1, no model answered; configured model: llama-3.3-70b-versatile, base URL: https://api.groq.com/openai/v1)
- Prompt version: triage-v1
- Dataset: evals/triage/dataset.jsonl (10 cases)

| Metric | Value | Pass (not enforced) |
|---|---|---|
| Urgent recall | 4/4 (100%) | ✅ |
| Priority accuracy | 10/10 (100%) | ✅ |
| Schema-valid rate | 0/10 (0%) | n/a (no model answered) |
| Red-flag recall | 12/13 (92%) | ✅ |
| Unsupported claims | 0 | ✅ |
| Latency p50 / p95 | 0.1 ms / 1.0 ms | report only |

Thresholds: urgent recall 100%, accuracy ≥ 80%, schema-valid 100%, red-flag recall ≥ 80%, unsupported claims 0.

## Per case
| Case | Expected | Got | Source | Red flags | Latency |
|---|---|---|---|---|---|
| T01 | URGENT | URGENT | RULE_BASED_FALLBACK | weight loss; anaemia | 1.0 ms |
| T02 | ROUTINE | ROUTINE | RULE_BASED_FALLBACK | – | 0.3 ms |
| T03 | SOON | SOON | RULE_BASED_FALLBACK | – | 0.1 ms |
| T04 | URGENT | URGENT | RULE_BASED_FALLBACK | difficulty swallowing; lost some weight | 0.1 ms |
| T05 | URGENT | URGENT | RULE_BASED_FALLBACK | chest tightness on walking | 0.1 ms |
| T06 | ROUTINE | ROUTINE | RULE_BASED_FALLBACK | – | 0.1 ms |
| T07 | SOON | SOON | RULE_BASED_FALLBACK | – | 0.1 ms |
| T08 | URGENT | URGENT | RULE_BASED_FALLBACK | mole on the back that the patient's partner says has grown | 0.1 ms |
| T09 | ROUTINE | ROUTINE | RULE_BASED_FALLBACK | – | 0.1 ms |
| T10 | SOON | SOON | RULE_BASED_FALLBACK | – | 0.1 ms |

## Failures
- T07: missing "aortic stenosis" (source RULE_BASED_FALLBACK)
