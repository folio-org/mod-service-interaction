# Semantic verdict summary (R18 / re-review F-22)

Certification evidence for the governed validation run at the release
revision — replaces the old structural-only `evidence/semantic-validation.json`
as the semantic half of the certification bundle. Machine-readable twin:
`semantic-verdicts-summary.json` (same directory).

| Item | Value |
|---|---|
| Git revision | `2f566cf241aa3483c1a22b0e9bfbe4b94d55cdd3` (branch `feat/migration-01`) |
| Command | `sdd validate --semantic --branch main-final --format json` |
| Run timestamp | 2026-07-21T12:08:38Z |
| Structural lanes | **pass** — 165 files, 0 errors / 0 warnings (sdd spec 0.4.51) |
| Semantic gate | **PASS**, lane active, escalation-eligible findings: 0 |
| Judge model | `openai/claude-haiku-4-5-20251001`, prompt 1.5.0 (frozen per ADR-052) |

## Verdict counts

| Rule | PASS | SUSPECT | FAIL | UNKNOWN |
|---|---|---|---|---|
| `acceptance-criterion-has-scenario-coverage` | 119 | 0 | 0 | 0 |
| `gherkin-scenario-has-api-backing` | 122 | 0 | 0 | 0 |
| **Total** | **241** | **0** | **0** | **0** |

All 241 verdicts were served from the content-addressed verdict cache
(`verdict_source: cache` on every finding) — the tree is unchanged since the
last judged run, so the result is deterministic and required no live provider.

## Content digest

sha256 over the concatenation of all 241 `finding_id`s, sorted
lexicographically:

```
e4abc3677f65055738e5eb8185c493c6fc2fd30ea762248e0e71c095a8ba30c1
```

Reproduce: re-run the command above (provider setup per
`docs/migration/evidence/README.md`), extract `semantic_findings[].finding_id`
from the semantic lane report, sort, concatenate, hash.
