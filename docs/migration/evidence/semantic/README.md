# Semantic verdict corpus (R27; review №3 F-22 residue)

Review №3 noted that the retained evidence carried only semantic *summaries*
(`r18/semantic-verdicts-summary.{json,md}`: gate status + verdict counts) —
the per-file finding IDs and verdicts themselves were absent, so the
semantic-gate claim could not be audited from the bundle alone. This
directory retains the full corpus.

Captured 2026-07-22 from the working tree at branch `feat/migration-01`
immediately after the R28 traceability correction (TRC-023), i.e. the spec
state the corpus judges is the committed one:

```
sdd validate --semantic --branch main-final --format json
```

The CLI writes two JSON documents — the structural validation result to
stdout and the semantic advisory document to stderr. Both are retained
verbatim (pretty-printed with `json.dump(indent=2)`, content untouched):

| File | Stream | Content | sha256 |
|---|---|---|---|
| `structural-validation.json` | stdout | 165 files, 0 errors / 0 warnings, per-rule coverage table, `pass: true` | `f3bf87fe7f4ca88b2409762b293d04506e898f0901a245e0e11a61d56007ad12` |
| `semantic-verdicts-full.json` | stderr | `gate_status: PASS`, `escalation_eligible_count: 0`, per-rule gates (both active rules PASS), and **all 245 `semantic_findings`** — each with `finding_id`, `rule_id`, `source_spec_id`, `target_spec_id`, `verdict` (245× PASS, 0 SUSPECT / 0 FAIL / 0 UNKNOWN), `confidence`, `rationale`, verbatim `evidence_span_source`/`evidence_span_target`, `model_id`, `prompt_version`, `verdict_source` | `37195120b4361ef2e5e6eb0413dfc57b7562eb7c41563eef071c95d073d86e28` |

Provenance notes:

- Judge model per finding: `openai/claude-haiku-4-5-20251001` via the local
  semantic proxy (127.0.0.1:54001), prompt versions recorded per finding.
- `verdict_source: cache` on all 245 — the verdicts were minted by earlier
  live judge calls in this remediation program and are replayed from the
  verdict cache for unchanged (source, target, prompt) triples; a changed
  pair re-judges live (the R28-touched TRC-023 has no semantic-lane rule, so
  no re-judgement was triggered by it).
- R-CERT recaptures this corpus at the certification HEAD; this snapshot is
  the R27 retention baseline.

## R-CERT recapture (`rcert/`)

Recaptured 2026-07-22 at the certification HEAD (`63c8b2f`, the last commit
touching `specs/` or `src/`; the R-CERT commits add only `docs/` evidence, so
the spec state judged here IS the certified one). Same command, same
two-stream persistence:

| File | Content | sha256 |
|---|---|---|
| `rcert/structural-validation.json` | 165 files, 0 errors / 0 warnings, `pass: true` | `970b45d7f49fc613b14df20d52fa2a88ea4db16c265e1514bd8492d60395e095` |
| `rcert/semantic-verdicts-full.json` | `gate_status: PASS`, 245 findings — 245 PASS / 0 SUSPECT / 0 FAIL / 0 UNKNOWN, all `verdict_source: cache` (spec tree unchanged since the R28 baseline capture, so every (source, target, prompt) triple replays) | `29edbf12844f9124c98fb02367cfc33cfae6aaac5aaae20c2724ae82d132a753` |

## R-VER recapture (`rver/`) — fresh verdicts on the M9-touched specs

Recaptured 2026-07-23 at the M9 closing HEAD (last commit touching `specs/` or
`src/`: `66c5435`; the R-VER commits add `docs/` evidence plus two
R-VER-traced harness fixes). Same command, same two-stream persistence — with
one deliberate difference from R-CERT: before the run, the content-addressed
verdict cache entries (`.sdd/semantic/verdicts/`) for every M9-touched pair
were evicted (19 findings / 22 cache files, a few superseded duplicates), so
the reviewer's "fresh verdicts on every M9-touched spec" gate is satisfied by
live model judgments, not cache replay.

| File | Content | sha256 |
|---|---|---|
| `rver/structural-validation.json` | 166 files, `pass: true` — 0 errors, 1 advisory warning (`adr-has-architecture-context` on the new ADR-013, which references REQ-016 rather than an architecture component) | `d3b6aa7349944fdb131a22a746caf98c5c9a2b9953e8a903477ebbe550058238` |
| `rver/semantic-verdicts-full.json` | `gate_status: PASS`, 249 findings — 249 PASS / 0 SUSPECT / 0 FAIL / 0 UNKNOWN, 0 escalation-eligible; `verdict_source`: **230 cache + 19 `model` (fresh)** — the fresh set is exactly the M9-touched pairs: REQ-022 `listing-grammar.yaml` AC1–AC7 ↔ `listing-grammar.feature` scenarios, REQ-020 `tenant-lifecycle.yaml` AC6 ↔ the omitted-purge and non-Boolean/duplicate-purge scenarios, and their `servint-number-generators.yaml` / `servint-tenant.yaml` API backings | `f589c6cead8a8f11e6dea0812f63f3829cd58efdb94d02abb90888a507dd2868` |
