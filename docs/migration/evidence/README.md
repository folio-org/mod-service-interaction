# Semantic-validation evidence (review condition C7 / workstream R11)

`semantic-validation.json` is the machine-readable output of the full
governed validation run — parse → schema → cross-reference rules → graph
load → advisory semantic lane — captured after the M6 remediation:

```
sdd validate --semantic --branch main-final --format json
```

Result at capture time: **0 structural errors / 0 warnings; semantic gate
PASS — verdict counts PASS=238 SUSPECT=0 FAIL=0 UNKNOWN=0** (stable across
three consecutive runs).

## Reproducing the run

The semantic judge is configured in `sdd.config.yaml#/semantic/model`
(frozen per ADR-052): provider `openai`-compatible, model
`claude-haiku-4-5-20251001`, `base_url http://127.0.0.1:54001/v1` — a
local LLM proxy. To reproduce:

1. Start the local LLM proxy on port 54001 (or point `base_url` at any
   OpenAI-compatible endpoint serving the pinned model).
2. Export `OPENAI_API_KEY` (the key is never stored in the repo).
3. From the repo root: `sdd validate --semantic --branch main-final --format json`.

Judge verdicts are cached by content hash, so a re-run on an unchanged
tree is fast and deterministic; edited spec files re-judge only their
affected pairs. If the provider is unreachable the affected pairs report
`UNKNOWN (provider_unavailable, disposition=defer)` rather than failing
the gate — re-run once the provider is back to obtain a fully judged
result (this addresses the C7 reproducibility gap the review hit).
