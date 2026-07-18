# Remediation plan №3 (M8) — response to BMAD Party re-review №3

**Produced:** 2026-07-21 via the BMAD correct-course workflow (autonomous batch mode)
**Trigger:** `docs/migration/bmad-party-review-3-report.md` — third **NO-GO** against
`feat/migration-01` HEAD `950764a` (review checkout `b5e1a07`)
**Approval:** user directive 2026-07-21 — "prepare the detailed remediation plan and
then execute it" (plan + execution pre-approved; per-workstream commits authorized,
push forbidden, PR gated)

---

## 1. Issue summary

Review №3 independently reproduced **every core migration claim** (E1–E12: clean
build, rebuilt Java 21 image, unmodified harness 28/28 both sides, 20-client pool
burst, real Okapi 7.0.6 lifecycle, populated adoption, semantic 241/241, both
drift-gate mutations biting, 2,000-tenant cache stress) and CLOSED 10 of review №2's
16 items under the strict original-reproduction rule. The module core is validated.

The NO-GO rests on a new register — **1 Blocker, 8 Major, 2 Minor** — concentrated
in three areas:

1. **One production-code regression (F-26).** R16's empty-RHS remediation is not
   clause-local and not side-effect-free: `DroppedClauseException` is caught only
   around the *top-level* filter node, so a compound with one empty leaf drops the
   whole filter (port returns all rows; legacy returned zero); the INNER JOIN from
   `resolvePath` is created *before* the empty-RHS check (abandoned join filters
   null-associated rows); and the property-validity throw precedes the empty-value
   check (`notAProp==` → 400, violating REQ-022 AC1). F-21 is therefore REGRESSED.
2. **Operational tooling defects (F-27..F-31, F-35).** `rollout.sh` records tenant
   and wave PASS without performing or verifying the Okapi routing change (Blocker);
   retries destroy ledger-referenced evidence; failure handling has unrecorded-
   mutation and no-timeout gaps; tenant-file normalization corrupts ids; the runbook's
   manual timer check falsely diagnoses a healthy state as failure.
3. **Governance/safety edges (F-32, F-33, F-34, F-36 + residue F-13/F-22).**
   Omitted `purge` is a schema-valid destructive default; the tenant API sits outside
   the compile drift gate while the README claims full coverage; the rehearsal proxy
   records credentials into retained evidence; cache metrics are registered but not
   exposed; TRC-023 and the R18 "bundle alone" claim are stale/overbroad.

All pins were re-verified by the executing agent against the working tree before this
plan was drafted; every checked pin holds. No falsification candidates this time.

## 2. Impact analysis (checklist digest)

- **Program impact.** M0–M7 stand; nothing is invalidated — review №3 *confirms* their
  substance. A new remediation milestone **M8** is added (Direct Adjustment), followed
  by a re-certification round and (user-gated) re-review №4.
- **Spec impact (governed SDD sessions required).** REQ-022 (listing grammar: compound
  empty-RHS semantics are under-specified — current AC1 was derived from single-filter
  evidence only; possible new deviation **D-32**); `servint-tenant.yaml` +
  `tenant-attributes-dto.yaml` + ADR-012 (explicit-destructive-intent contract);
  TRC-023 (stale adoption-coverage statement).
- **Code impact.** `KiwtListing.java` (F-26 — the only `src/main` change);
  `ServintTenantController`/DTO validation (F-32); `application.yml` metrics exposure
  (F-36); one new completeness test for the tenant contract (F-33).
- **Ops/docs impact.** `rollout.sh` (major overhaul), `cutover-runbook.md` (Phases 4/5/6),
  `README.md` (drift-gate claim), `proxy.py` + committed r20-rehearsal headers
  (redaction), `evidence/r18/README` (provenance wording), wire-compat deviation
  dossier, completion report (M8 annex).
- **Test impact.** `KiwtListingGrammarIT` compound/empty-RHS matrix + harness
  `probes.tsv` rows; `TenantEnableIT` destructive-shape rejections; tenant-surface
  completeness test; metrics-exposure assertion.
- **Not affected.** Legacy module under `service/` (never modified); wire surface of
  all six generated APIs; data-adoption path; attestation; the eight-generator seed.

**Path forward evaluated:** Direct Adjustment (add M8 workstreams) — effort Medium,
risk Low/Medium — **selected**. Rollback of R16 rejected (the per-filters-param model
is correct; only leaf-level handling is wrong). MVP re-scope rejected (review №3 shows
the cutover bar is reachable; merge-gate items are all fixable).

## 3. Workstreams

Numbering continues from M7 (R13–R21). Each workstream ends in its own commit.
Standing rules: legacy `service/` untouched; every spec change through a formal
governed SDD session with `sdd validate --semantic --branch main-final`; one Maven
build at a time; rig usage serialized; context7 documentation check before touching
Spring/folio-spring configuration surfaces.

### R22 — Clause-local, side-effect-free empty-RHS semantics (F-26, F-21, part of F-16)

The only production-code workstream. Two phases, empirical first:

**Phase 1 — legacy oracle (rig: legacy jar :8080 + testing_pg, still running).**
Booted-legacy probe matrix over number generators (12-row fixture), captured as
`docs/migration/evidence/r22-legacy-compound/`:

| # | filters | question |
|---|---|---|
| 1 | `code==alpha&&prefix==` | empty leaf under AND (review observed: 200 `[]`) |
| 2 | `(code==alpha||prefix==)` | empty leaf under OR (review observed: 200 `[]`) |
| 3 | `prefix==` (alone) | re-confirm top-level drop (REQ-022 AC1 baseline) |
| 4 | `checkDigitAlgo.value==` (alone) | dotted-path empty: dropped with NO join residue? |
| 5 | `code==alpha&&checkDigitAlgo.value==` | dotted empty leaf inside compound |
| 6 | `notAProp==` (alone) | unknown property + empty RHS: drop vs 400 |
| 7 | `code==alpha&&notAProp==` | unknown-prop empty leaf inside compound |
| 8 | `!(prefix==)` | empty leaf under NOT |
| 9 | `code==alpha&&prefix==&&code==alpha` | multiple leaves, one empty |
| 10 | `code==` | empty RHS on a non-null column inside/alone (control) |

**Phase 2 — decide, specify, implement.** From the oracle: pin the governed compound
semantic (working hypothesis, to be confirmed/refuted: an empty-RHS leaf *inside a
compound* evaluates as an always-false criterion, while a *top-level* empty-RHS filter
is dropped entirely; any deliberate port divergence registers as **D-32**). Then:

- Governed SDD session: extend REQ-022 with compound acceptance criteria (+ behavior
  scenario refs); register D-32 in the dossier only if the port deliberately differs.
- `KiwtListing.java`: make leaf validity checks (empty RHS, unknown property) happen
  *before* any `resolvePath` join resolution; apply the governed per-context semantic
  (drop at top level, legacy-observed semantic inside compounds); `notAProp==` at top
  level follows AC1 (drop, not 400). No abandoned joins may reach the query.
- `KiwtListingGrammarIT`: add all ten matrix rows (port vs pinned expectations,
  including the "equals unfiltered listing" assertion for dropped dotted-path leaves).
- Harness: add the compound cases to `probes.tsv` (both-side verification) and, if
  D-32 exists, to `deviation-allowlist.tsv`.

**Acceptance:** all ten probes green against booted legacy AND port with identical (or
governed-deviation) results; `mvn -B verify` green; semantic lane PASS.
**Effort:** M-H. **Depends on:** running rig.

### R23 — rollout.sh: real cutover, immutable evidence, fail-safe recovery (F-27 Blocker, F-28, F-29, F-30, F-35)

- **Cutover is the checked step (F-27).** When `OKAPI_URL` is set (production mode),
  enable runs through Okapi's install API (`POST /_/proxy/tenants/<T>/install`), then
  the script *verifies* `GET /_/proxy/tenants/<T>/modules` lists `MODULE_TO`, then
  smokes **through Okapi**, and only then appends PASS with the observed routing state
  in the ledger row. Module-direct mode survives only as an explicit `MODE=direct`
  pre-verification pass whose ledger rows say `verified-direct`, never `complete`.
  Rollback performs the inverse Okapi transition and re-verifies routing.
- **Immutable evidence (F-28).** Evidence at `$OUT_DIR/<wave>/<run-id>/<tenant>/` with
  a fresh run-id per invocation; directory reuse refused; every evidence file's sha256
  recorded in its ledger row; `catalog.diff` never deleted.
- **Fail-safe handling (F-29).** Preflight validates `MODULE_TO`/`MODULE_FROM`/
  `TENANT_PARAMETERS` as JSON-safe (python3), preflights a ledger append, and DRY_RUN
  refuses malformed configuration; all curl calls get `--connect-timeout`/`--max-time`;
  an ambiguous enable (transport error after send) is reconciled against actual module
  state before being classified; a failed rollback sets `HARD_STOP`.
- **Tenant normalization (F-30).** Trim outer whitespace only; reject internal
  whitespace and duplicates; enforce `len(tenant) + 24 ≤ 63` for the derived schema.
- **Executable recovery (F-35).** Real (non-dry) runs with rollback exposure require
  `OKAPI_URL`; the recorded manual command is fully resolved and includes the token
  header.

**Acceptance:** `bash -n` + shellcheck clean; DRY_RUN rejects the review's malformed-
JSON case; unit-style probes for normalization; full empirical validation deferred to
R-CERT (real-Okapi wave with routing verification, forced failure, retry-new-run-dir,
rollback verify, resume). **Effort:** H. **Independent.**

### R24 — Cutover runbook corrections (F-31, F-27 doc side, F-12 residue)

- Phase 4 rewritten around the corrected `rollout.sh`: Okapi install is the checked
  cutover step; routing verification commands included.
- Phase 5 timer verification replaced: check `GET /_/proxy/tenants/<T>/timers` for the
  `mod-service-interaction_0` entry and observe a scheduled execution (or module log
  line); the manual POST through Okapi is documented as *expected to 404* (system
  `_timer` interface, not the public `servint` surface) — the false-abort instruction
  removed; direct-module POST retained as an explicitly-labeled diagnostic.
- Phase 6 manual-rollback command aligned with R23's resolved, authenticated form.

**Acceptance:** runbook Phases 4–6 walk cleanly against the R-CERT rehearsal.
**Effort:** L-M. **Depends on:** R23 (tool shape must exist first).

### R25 — Explicit destructive intent for tenant purge (F-32)

- Governed SDD session: `servint-tenant.yaml` + `tenant-attributes-dto.yaml` +
  ADR-012 — a body with blank/absent `module_to` and *omitted/null* `purge` is
  **rejected 400** (no destructive default); purge requires explicit `purge=true` with
  `module_from` present; disable requires explicit `purge=false`. Update the D-26
  dossier entry. Wire-safe by R20 evidence: Okapi 7.0.6 always sends the flag
  explicitly (all five captured bodies).
- Code: validation in the tenant controller path (nullable `Boolean` inspection — the
  current destructive default must disappear, not move).
- `TenantEnableIT`: omitted-purge + blank `module_to` → 400 and schema intact;
  omitted `module_to` + omitted purge → 400; explicit shapes unchanged (5/5 existing
  assertions stay green).

**Acceptance:** new ITs green; full lifecycle re-verified in R-CERT rehearsal.
**Effort:** M. **Independent.**

### R26 — Tenant contract under an explicit gate; honest README (F-33)

Chosen remedy: narrow the README claim (the six generated surfaces are compile-
enforced; the tenant surface is contract-checked at runtime) **plus** a completeness
test asserting the served `/_/tenant*` surface (paths, methods, statuses) matches
`specs/api/servint-tenant.yaml` — implemented against Spring's handler mappings so a
spec/controller drift fails the suite. Generating a seventh interface is rejected: the
controller deliberately implements folio-spring's tenant contract (ADR-012), and a
competing generated interface would fork that inheritance.

**Acceptance:** test fails on a mutated spec copy (drift probe), passes at HEAD;
README corrected. **Effort:** L-M. **Independent.**

### R27 — Evidence hygiene: redaction, atomicity, provenance honesty (F-34, F-22 residue)

- `proxy.py`: redact `Authorization`/`X-Okapi-Token`/`Cookie` values (keep name +
  sha256 prefix); write captures atomically (tmp + rename); a capture failure after a
  forwarded mutation logs an explicit `capture-failed` marker while still relaying the
  true upstream response.
- Committed `r20-rehearsal` header files: redact in place (dev-rig dummy tokens, but
  the pattern must be safe); note the redaction in the evidence README.
- `evidence/r18` + `r20` READMEs: replace the "bundle alone" claim with an exact
  provenance statement — what the bundle proves by itself (response hashes, diffs,
  bodies) vs what requires a rebuild (JARs are timestamp-nondeterministic).
- Retain the semantic verdict corpus: persist the full `--semantic --format json`
  output (per-file finding IDs + verdicts) under `docs/migration/evidence/semantic/`.

**Acceptance:** no credential-shaped values in retained evidence; recomputed hashes
documented. **Effort:** L-M. **Independent.**

### R28 — Traceability honesty (F-13 residue)

Governed SDD session: TRC-023 updated to cite `AdoptedSchemaUpgradeIT` and
`FreshDdlCatalogIT`; the "not yet by an in-suite IT" clause removed; wording aligned
with the actual assertion strength (catalog + counts + wire readback — not bit-for-bit
row content, which stays an honest residual).

**Acceptance:** semantic lane PASS on the modified TRC. **Effort:** L. **Independent.**

### R29 — Operational exposure of cache metrics (F-36)

Expose the metrics actuator endpoint under the deployment's admin surface
(`management.endpoints.web.exposure.include` + folio-convention path), after a
context7 check of current Spring Boot actuator guidance; extend
`WidgetCacheCapacityIT` (or a focused IT) to fetch `cache.size`/`cache.evictions`
over HTTP, not just the in-process registry. No new exporter dependency.

**Acceptance:** IT green over the wire. **Effort:** L. **Independent.**

### R-CERT — M8 certification round

After R22–R29 land: rebuild the image from HEAD (the `r18` image is stale by
construction); `mvn -B verify` (full suite incl. new ITs); `sdd validate --semantic
--branch main-final` (expect 165+ files, 0/0, all-PASS); re-run the unmodified harness
both sides including the new compound probes; **real-Okapi re-rehearsal** exercising
the corrected `rollout.sh`: control wave with routing verification, forced mid-wave
failure, retry into a fresh run directory with intact prior evidence, authenticated
rollback with verified inverse routing, resume; evidence forensics recompute; dossier +
completion-report M8 annex; journal closure. Review №4 prompt/convening stays
user-gated.

**Effort:** M-H (dominated by the rehearsal).

## 4. Dependencies and execution sequencing

```
R22 (oracle → SDD → code → ITs → harness)   [rig]
R23 ──→ R24                                  [no rig until R-CERT]
R25, R26, R27, R28, R29                      [independent]
ALL ──→ R-CERT                               [rig + rebuilt image + Okapi]
```

Round plan (compaction pause between rounds, per standing rule):

| Round | Content | Commits |
|---|---|---|
| 0 | this plan | `docs(m8): remediation plan 3` |
| 1 | R22 | oracle evidence + SDD session + fix + ITs + harness |
| 2 | R23 + R24 | rollout overhaul; runbook corrections |
| 3 | R25 + R26 | purge contract; tenant gate + README |
| 4 | R27 + R28 + R29 | evidence hygiene; TRC-023; metrics |
| 5 | R-CERT | rehearsal evidence + dossier/report closure |

Serialization: one Maven build at a time; the rig serves R22 (oracle) and R-CERT
(rehearsal) — no concurrent rig mutation; SDD sessions strictly sequential (one open
changeset at a time).

## 5. Handoff and merge-gate mapping

**Scope classification:** Moderate — executed directly by the developer agent under
the standing full-autonomy rule; no backlog reorganization beyond adding M8.

Review №3 merge-gate item → workstream:

| Gate item | Workstream(s) |
|---|---|
| 1. F-27/F-28/F-29/F-30/F-31/F-35 + corrected tool rerun through real Okapi | R23, R24, R-CERT |
| 2. F-26 fixed or governed as D-32, pinned in IT + harness vs booted legacy | R22 |
| 3. F-32 explicit-destructive-intent contract | R25 |
| 4. Tenant API under an explicit gate (F-33) | R26 |
| 5. Evidence claim + TRC-023 corrected; verdict corpus retained w/o secrets (F-22/F-13/F-34) | R27, R28 |
| (post-gate re-verification set) | R-CERT |

Success criterion for M8: every gate item closed with reproducible evidence, full
suite + semantic lane green at the closing HEAD, and the program ready for the
user-gated review №4 / PR decision.
