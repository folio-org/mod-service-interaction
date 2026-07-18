# Remediation plan 2 (M7) — response to independent re-review №2

Produced through the BMAD correct-course workflow (autonomous batch mode) on
2026-07-21, triggered by `docs/migration/bmad-party-review-2-report.md`
(BMAD Party Mode re-review №2, NO-GO against `0d7dd33` on
`feat/migration-01`). Companion to `remediation-plan.md` (M6, which answered
review №1); workstream numbering continues from M6's R0–R12 as **R13–R21**.

## 1. Issue summary

Re-review №2 confirmed the M6 remediation core (6 findings CLOSED, all
positive evidence independently reproduced — release path, 64-bit, seeding
matrix, tenant isolation, display identity, fresh DDL, storage rollback,
semantic 238/238, full 3+71 suite) but returned a second NO-GO on:

- **2 REGRESSED**: F-02 (the governed `_tenant` 2.0 disable model is
  factually false — Okapi *does* POST `/_/tenant` with `module_from` and no
  `module_to` on disable) and F-11 (the hardened harness cannot populate a
  real legacy tenant — two legacy-side status expectations are wrong and
  were never re-run after fail-closed assertions were added).
- **9 new findings** F-17..F-25: pool starvation on pool-size first-use
  bursts, the false disable model (proposed D-26), D-2 wrong again (legacy
  widget-def POST is 201, not 500), dashboard create 200→201 ungoverned
  (proposed D-27), six unregistered parser/error deltas (proposed
  D-28..D-31), non-auditable committed evidence, unbounded per-tenant
  widget cache, non-executable rollout Phase 4, and a default-method drift
  guard that does not actually exist.
- **7 must-fix items** before PR merge (report §"Must fix before PR merge").

The executing agent independently verified the four highest-impact claims
before planning: the Okapi guide passage at the cited commit (disable POSTs
`module_from` without `module_to`), the `@Transactional` +
`PROPAGATION_REQUIRES_NEW` starvation mechanism
(`NumberGeneratorsController:28` + `NumberGeneratorService:57`), the
unbounded `tenantCaches` `ConcurrentHashMap`
(`WidgetDefinitionService:47`), and the harness history (legacy-side
expectations date from pre-assertion M4 and were only ever re-run against
the port in R12). All four are real.

**Root-cause pattern.** Every surviving finding lives in territory never
empirically exercised: the real Okapi control plane, the legacy side after
harness hardening, pool-size load, multi-hundred-tenant scale, and actually
executing the rollout procedure. Everything that was empirically pinned
closed. M7 therefore leads with an empirical round (R13) before any spec
correction, and every correction must land with a pin that actually runs.

## 2. Impact analysis (correct-course checklist result)

| Area | Impact |
|---|---|
| Mandate (task-definition.md) | Unchanged and still achievable. "Legacy defects need not be reproduced bug-for-bug, but every deviation must be governed" directly drives the F-21 disposition. |
| M0 spec universe | ADR-012, REQ-020, `tenant-disable.feature`, D-17, TRC-020 assert a falsehood (disable = no call). REQ-022's empty-RHS AC is contradicted by empirical legacy behavior and must be re-verified. D-2 must be re-corrected; D-26..D-31 must be registered. All flow through governed SDD sessions. |
| M3 implementation | Two code defects (F-17 transaction shape, F-23 unbounded cache) + one hardening (F-25 drift gate). No architectural rework. |
| M4 adoption proof | Missing a *committed, named* populated-adoption regression; R13's legacy rig run yields the pg_dump fixture that makes one executable in CI. |
| M5 cutover package | Harness legacy oracle wrong (F-11/F-19/F-20), evidence bundle not auditable (F-22), rollout Phase 4 not executable (F-24). |
| Epics/sequencing | No epic obsolete, no rollback beneficial, MVP unchanged. Direct Adjustment selected. |

## 3. Recommended approach

**Direct Adjustment** — nine new workstreams inside the existing structure.
Rationale: the two REGRESSED items are spec/test corrections, not code
rewrites; the code defects are localized (transaction annotations, one
cache field, one codegen flag); the remaining work is empirical evidence
and operational scripting. Rollback of M6 work would discard independently
re-confirmed closures. Estimated effort ≈ 50–60 % of M6.

Standing rules carried forward: commits per workstream authorized, **no
push**, **no PR**; every spec change through a full governed SDD session
with `--semantic` validation; legacy module untouched; one Maven build at a
time; subagent delegation to protect context; compaction pauses between
workstreams.

## 4. Workstreams

Dependencies: R13 → {R16, R19}; R14+R15+R16 → R18; R15 → R20(rehearsal);
R21 last. R14, R15, R17, R20(script), R21(gate) are mutually independent.

### R13 — Legacy oracle re-establishment (empirical; runs first)

Fixes the evidence base that F-11/F-19/F-20/F-21 proved rotten.

1. Boot the real legacy module against a fresh Postgres (M4 rig:
   `service/` + `tools/testing` compose), enable a clean tenant.
2. Capture with retained raw bodies, twice (fresh tenant each run):
   dashboard POST status; widget-definition POST (resolve 201-vs-500
   against the review's two 201 observations — if environment-dependent,
   document the discriminating factor); the six F-21 probe rows
   (`enabled==notabool`, empty RHS, escaped literal, unbalanced paren,
   `>notanumber`, 10k filter) plus `f14-invalid-boolean`; dashboard-create
   and any other populate.sh legacy expectation.
3. Run the full legacy-side `populate.sh` with expectations *corrected to
   the observed statuses* and capture 23/23 probes with normalized bodies
   retained (feeds R18).
4. `pg_dump` the populated tenant schema (DDL + data) as the checked-in
   adoption fixture for R19.

Deliverables: `docs/migration/evidence/r13-legacy/` (raw + normalized
captures, commands, jar/HEAD identification), adoption fixture SQL.
Acceptance: every legacy-side status claim used anywhere downstream cites a
capture file from this run.

### R14 — getNextNumber transaction restructure + pool-size pin (F-17)

1. Remove the connection double-acquisition: `getNextNumber` must not hold
   an outer controller transaction around the service's `REQUIRES_NEW`
   boundary. Audit **all** controllers for class-level `@Transactional`
   combined with nested new-transaction services (`DashboardService.itemTx`
   is the known second instance).
2. Define overload behavior: with single-acquisition, bursts ≥ pool size
   queue on Hikari and succeed; document this in the completion report.
3. New IT: ≥ 20 concurrent `getNextNumber` requests against one fresh
   generator/sequence with the default 10-connection pool — assert all 200,
   values unique and gapless; raise `NumberGeneratorConcurrencyIT` load to
   ≥ 16 threads.

Acceptance: new IT green in `mvn verify`; the review's 20-client burst
reproduction returns 20×200 against the rebuilt image (verified in R18).

### R15 — Disable model correction, D-26 (F-18, REGRESSED F-02)

1. **Governed SDD session**: revise ADR-012 (disable is a
   `module_from`-only POST, not a no-call), REQ-020, rewrite
   `tenant-disable.feature`, correct D-17, register **D-26**, correct
   TRC-020, adjust any exemption referencing the no-call model. Design
   decision to govern: on a disable-shaped body (`module_from` present,
   `module_to` absent, `purge` false) the module answers 204 **without**
   running Liquibase or seeding, and evicts that tenant's caches — verified
   against folio-spring-support sources (`TenantService`/
   `TenantAttributes`) before authoring.
2. Implement in `ServintTenantService` (disable-shape detection; skip
   DDL/seed; evict).
3. Replace `disableWithoutPurgeLeavesSchemaIntact` with an Okapi-shaped
   disable IT that actually POSTs the official body and asserts: schema
   intact, rows intact, no re-seed side effects, caches evicted, 204.
   Purge IT keeps its real POST.

Acceptance: semantic lane PASS on the revised specs; new disable IT green;
D-26 registered with the guide citation.

### R16 — Listing/error governance round 2 (F-19, F-20, F-21, F-14)

From R13 captures only — no static-analysis pins.

1. **Governed SDD session(s)**: re-correct **D-2** (expected: legacy 201 +
   created body vs port 405) with consumer analysis for direct
   widget-definition POSTs; register **D-27** (dashboard create: legacy 200
   vs port 201) with consumer analysis; register **D-28..D-31** for the
   parser/error rows where the disposition is "keep port behavior, govern
   the delta" (per mandate: legacy 500s on malformed input are defects not
   to be reproduced); where legacy behavior is *meaningful* and the port
   silently diverges (empty-RHS row: legacy drops the clause and returns
   all rows, the port's IS-NULL interpretation returns none), correct
   REQ-022's AC and the port implementation to match legacy.
2. Fix `ErrorEnvelopeMatrixIT`'s false premise (invalid Boolean: legacy
   500s; the port's treat-as-absent behavior is a governed deviation, not
   legacy parity); extend `KiwtListingGrammarIT` with all six directed
   rows; add every new deviation to `deviation-allowlist.tsv`; split
   per-side expectations in `probes.tsv`/`populate.sh` from R13 evidence.

Acceptance: unmodified harness passes on both sides (proven in R18); every
F-21 row either matches legacy or cites a D-number; semantic lane PASS.

### R17 — Bounded per-tenant widget cache (F-23)

Replace the raw `ConcurrentHashMap` in `WidgetDefinitionService` with a
bounded Caffeine cache (size/weight bound + TTL; current docs via context7
before implementation), keep per-tenant isolation and explicit eviction,
expose size/eviction Micrometer metrics. New IT: several hundred simulated
tenants → cache size respects the bound; eviction works; isolation ITs
still green. Document the capacity figure.

### R18 — Harness end-to-end + auditable evidence bundle (F-11, F-22)

After R14–R16 land:

1. Rebuild the release image; run the **unmodified** checked-in harness
   end-to-end on both sides (legacy from R13 rig, port from the image):
   populate → capture → diff-runs, exit 0 with the allowlist.
2. Commit an auditable bundle under `docs/migration/evidence/r18/`:
   normalized response bodies for **both** sides, both manifests with
   per-body sha256, `diff-runs` output, image digest + jar sha256, HEAD
   revision, exact command transcript, and a semantic verdict summary
   (per-pair verdicts + counts exported from `.sdd/semantic/verdicts`, with
   revision and content digest) replacing the structural-only
   `semantic-validation.json`.

Acceptance: a third party can re-verify every advertised hash from the
committed bundle alone.

### R19 — Populated-adoption + fresh-DDL regressions (must-fix 6, F-10/F-13 residue)

1. `AdoptedSchemaUpgradeIT`: restore the R13 pg_dump fixture (populated
   legacy schema) into the IT database, POST `/_/tenant` upgrade, assert:
   adoption changesets MARK_RAN, catalog/table counts unchanged, legacy
   rows read back on the wire, two-bucket seeding correct on an adopted
   schema (baseline vocabularies topped up idempotently, no duplicate
   generators).
2. `FreshDdlCatalogIT`: assert `refdata_category.internal` has
   `column_default IS NULL` plus the other R7-claimed catalog facts.
3. Correct TRC-023 to cite the now-committed evidence (fold into an R16 or
   R15 session to avoid a fourth SDD round if timing allows).

### R20 — Executable rollout + lifecycle rehearsal (F-24; condition 2 residue)

1. Replace runbook Phase 4 pseudocode with checked-in
   `docs/migration/harness/rollout.sh`: explicit env contract, wave loop,
   **append-only** ledger, per-tenant pre/post capture + smoke with return
   code checks, stop conditions including catalog/count failures, automatic
   failed-tenant rollback (existing Phase R), no undefined variables. Fix
   the descriptor path (`target/ModuleDescriptor.json`) and year handling.
2. Real-Okapi rehearsal: run an Okapi container in the rig, register both
   modules (`scripts/register_and_enable*.sh` lineage), drive
   install → upgrade → disable → purge through Okapi proper; record the
   observed `/_/tenant` bodies as evidence next to D-26.

Acceptance: `bash -n` + a dry-run mode for rollout.sh; rehearsal evidence
committed; runbook references the script instead of inlining pseudocode.

### R21 — Drift gate, docs, and re-certification (F-25 + closure)

1. Make spec→controller drift compile-enforced: `skipDefaultInterface=true`
   (or equivalent per current openapi-generator docs via context7) so
   generated interface methods are non-default; fix fallout; if
   infeasible, a CI route/override completeness check instead. Correct
   README/completion-report wording either way.
2. Final certification: full `mvn verify`; `sdd validate --semantic
   --branch main-final` PASS; unmodified harness both sides (from R18);
   completion-report "M7 remediation outcome" section with a per-finding
   F-17..F-25 + F-02/F-11 closure table; review-2 closure annex mapping
   the seven must-fix items to commits and pins.

## 5. Sequencing and effort

| Order | Workstream | Effort | Depends on |
|---|---|---|---|
| 1 | R13 legacy oracle | M | rig |
| 2 | R14 tx restructure | M | — |
| 3 | R15 disable model (D-26) | L | folio-spring source check |
| 4 | R16 listing/error governance | L | R13 |
| 5 | R17 bounded cache | S | — |
| 6 | R19 adoption/DDL pins | M | R13 |
| 7 | R20 rollout + rehearsal | M | R15 (rehearsal) |
| 8 | R18 harness + evidence | M | R14, R15, R16 |
| 9 | R21 drift gate + certification | S/M | all |

Estimated total ≈ 50–60 % of M6. Maven builds serialized; spec changes
only via governed sessions (expected: 1 session in R15, 1–2 in R16, TRC
corrections folded in); compaction pauses at workstream boundaries.

## 6. Residuals documented, not fixed (honest remainder)

Out of the reviewers' must-fix list and deliberately deferred, restated in
the completion report: multi-replica signing-key first-creation race
(condition 5 bound; single-replica deployment assumption documented),
SBOM content/publication validation (CI-side, exercised by the existing
workflow on merge), production-scale multi-hundred-tenant heap/load test
beyond the R17 capacity IT, and exhaustive grammar/property fuzzing beyond
the governed matrix.

## 7. Handoff

Scope classification: **Moderate** (backlog reorganization within the
migration project; no fundamental replan). Route: Developer agent (the
executing agent) implements R13–R21 in order, with per-workstream commits
(no push), subagent delegation for research/implementation, and the SDD
Arwen framework for every spec correction. Success criteria: all seven
must-fix items map to a commit + a pin that runs; the nine re-review
findings and both REGRESSED items have closure evidence; the re-review's
reproduction commands pass against the rebuilt artifact.
