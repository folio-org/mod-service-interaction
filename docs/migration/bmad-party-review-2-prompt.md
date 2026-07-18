# Review mission brief №2 — exhaustive re-validation after the M6 remediation

> **How to run**: convene BMAD Party Mode with this file as the opening intent
> (`/bmad-party-mode --mode subagent` or `--mode agent-team` so every persona
> thinks independently; add `--non-interactive` for an unattended run to a
> natural close). Suggested room: Analyst, Architect, Dev, QA/Test Architect,
> PM, Tech Writer — open-cast a Security reviewer and an SRE when their
> dimensions come up. Reuse the persona ownership from review №1 where it
> helps continuity, but every closure verdict must be re-derived, not
> remembered.

---

## Mission

You are the **independent review team** that returned **NO-GO** on the
`mod-service-interaction` Grails→Spring Boot migration at commit `d5ba8ea`
(your report: `docs/migration/bmad-party-review-report.md` — 3 Blockers,
13 Majors, 2 human-reviewer issues, 9 exact reconsideration conditions).

The implementer has since executed a remediation program (M6, plan at
`docs/migration/remediation-plan.md`) landing **19 commits on
`feat/migration-01`** on top of `d5ba8ea` (through
`1d298e0 docs(certification)…`), and claims: every finding closed, every
reconsideration condition met, the release artifact certified, and a
**GO** now warranted for production cutover.

Your job is to **try to break that claim — again**. You owe the implementer
nothing, and you owe your own previous report nothing either: a finding of
yours that the remediation proves was wrong gets retracted with evidence,
exactly as an unfixed finding gets re-confirmed with evidence. A cutover of
live production tenants rides on your verdict. Deliver
**go / conditional-go / no-go**, backed by findings you verified yourselves.

**Cardinal rule — empirical over textual.** This project's history now
proves it twice: four legacy-behavior claims from M0–M3 fell only when the
real legacy module was booted (M4), and the implementer's own R12
certification run falsified a dossier claim (D-2 said the port answers 201
on `POST /servint/widgets/definitions`; it answers 405 — the claim had never
been exercised). Any finding you can verify by *running something* must be
verified by running it. A claim you could not reproduce is UNVERIFIABLE,
never confirmed.

**Independence rules** (unchanged from review №1)

- Implementer documents — `completion-report.md` (including its new
  "M6 remediation outcome" section), `remediation-plan.md`, the dossier,
  the runbook, `.sdd/handoffs/` — are *claims under review*, not evidence.
- Reproduce before judging; attach command + output to every verdict.
- Preserve disagreement; the report carries both positions.
- The repository is **read-only** for you. Scratch work, captures, diffs go
  outside the repo. You change nothing, you commit nothing.

## Inputs

| Artifact | Role |
|---|---|
| `docs/migration/bmad-party-review-report.md` | Your review №1 — the findings register F-01..F-16 under closure test |
| `docs/migration/remediation-plan.md` | The remediation program (claim) |
| `git log d5ba8ea..HEAD` (19 commits) | The remediation delta — read it commit-by-commit |
| `service/` | The legacy module — **ground truth** for all behavior |
| `src/`, `pom.xml`, `descriptors/`, `Dockerfile`, `.github/workflows/`, `Jenkinsfile` | The port and its release path under review |
| `specs/` | Governed spec universe — now REQ-001..022, ADR-001..012, TRC-001..024 |
| `docs/migration/wire-compat-deviations.md` | Deviation register, now D-1..D-25 (claim of *completeness*) |
| `docs/migration/harness/` | Rewritten evidence harness: `probes.tsv`, `populate.sh`, `capture.sh`, `diff-runs.sh`, `deviation-allowlist.tsv` |
| `docs/migration/cutover-runbook.md` | Rewritten operational procedure (claim: cold-engineer-executable) |
| `docs/migration/evidence/` | Committed evidence: `semantic-validation.json`, `r12-port-capture-manifest.json`, reproduction README |
| `tools/testing/docker-compose*` | Postgres for the rehearsal rig |

Environment facts: port builds with Java 21 (`mvn -B verify`; suite now
claims **71 integration + 3 unit**); the release image builds from the root
`Dockerfile` (`mvn -B -DskipTests package && docker build .`) and serves
8081 with health at `/admin/health`; legacy jar
`service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar` on JDK 17
exactly as in review №1. The semantic lane needs the local LLM proxy
(`sdd.config.yaml#/semantic/model`: base_url `http://127.0.0.1:54001/v1`,
`OPENAI_API_KEY` in env) — provider setup documented in
`docs/migration/evidence/README.md`; if the proxy is down, pairs report
UNKNOWN — get it restarted rather than reporting UNVERIFIABLE.

## Mandate A — closure matrix: every finding of review №1 re-tested

For **each** of F-01..F-16 plus human-reviewer issues 1 and 2, verdict:

- **CLOSED** — you re-ran the finding's *original reproduction* (as recorded
  in your report) against HEAD and it no longer reproduces, AND the pinned
  regression the implementer claims (IT, CI job, harness probe) actually
  exists, actually runs, and actually asserts the failure mode. Name it.
- **PARTIALLY CLOSED** — the headline reproduction is fixed but a stated
  sub-case, edge, or the pinning evidence is missing/weaker than claimed.
- **OPEN** — still reproduces. Attach the reproduction.
- **REGRESSED** — the fix broke something else. Attach both.

Verify against the implementer's per-finding claims table in
`completion-report.md` §"M6 remediation outcome" — its commit hashes and IT
names are leads, not proof. Spot-checks that MUST happen: run
`TenantSeedingMatrixIT`'s no-parameter case yourself and compare against a
legacy no-parameter enable you booted (2 categories / 6 values / 0
generators / 0 widget types); re-run your F-04 probes (`nextValue>5`,
`perPage=0`, `perPage=101`, bad sort) against both modules; re-race
first-use `getNextNumber`; re-attempt the F-07 cross-tenant widget-cache
leak and the F-09 cross-dashboard display-data write with your original
requests.

## Mandate B — fresh adversarial sweep of the remediation itself

The remediation added ~3.5k lines of new production code and tests. New
code means new defects; your №1 findings say nothing about it. Hunt for
**D-26** — a behavioral difference absent from D-1..D-25 — and for defects
in the new machinery. Priority attack surface:

1. **`KiwtFilterParser` + rewritten `KiwtListing`** (the largest new
   surface). Fuzz the grammar against booted legacy: operator precedence,
   escaping (`\`, `%`, quotes), empty-RHS null semantics, value-first and
   ambiguous subject positions, middle-subject ranges, deep nesting,
   pathological inputs (unterminated groups, 10k-char filters — DoS?),
   dotted paths through every association, coercion of every property type,
   and the registered deviation D-19 (flat N-ary vs legacy top-two pairing):
   is its "no consumer impact" claim actually evidenced? Filters land in JPA
   Criteria — prove parameterization end-to-end (injection attempts through
   filter values, sort expressions, match properties).
2. **Seeding trigger rework** — `afterTenantUpdate` vs `loadReferenceData`/
   `loadSampleData` split: upgrade of an *adopted* legacy schema (does the
   unconditional baseline stay idempotent against legacy-seeded rows with
   legacy ids?), the `pg_advisory_xact_lock` in `RefdataService.lookupOrCreate`
   (deadlock ordering? `hashtext` collision behavior? lock held across the
   whole creation transaction?), the truthiness deviation D-18 with a real
   Okapi-shaped body.
3. **Concurrency fix** — the `ON CONFLICT DO NOTHING` insert path: are the
   SQL-inserted generator/sequence defaults byte-identical to the
   Java-seeded ones (formats, templates, version column, checkDigitAlgo
   linkage)? Race it harder than the IT does (more threads, generator+
   sequence created mid-race, purge racing generation).
4. **Caches** — per-tenant widget cache growth is unbounded across tenants
   (memory ceiling at multi-hundred-tenant scale?); eviction on upgrade vs
   the harvest-once-per-boot legacy semantics; key-pair cache `compute`
   atomicity under concurrent expiry.
5. **Error/leniency layer** — `KiwtParamLeniencyAdvice` scope: does the
   lenient binding leak onto non-listing endpoints or shadow validation the
   generated `@Valid` DTOs need? `DataIntegrityViolationException` → 409:
   any path where legacy answered 422/500 that consumers key on? Sanitation:
   hunt for any handler that still echoes constraint or SQL text.
6. **Codegen flip (useTags / flat package)** — diff the served route table
   pre/post flip (Spring mapping dump or descriptor vs `@RequestMapping`
   scan): all 48 operations still served with identical methods/paths?
   `DashboardDefinitionsApi`'s `/dashboard/definitions` (no `/servint`
   prefix) still routed and permission-checked? The corrected D-2 (405):
   confirm legacy consumers truly cannot have depended on the 500-with-side-
   effect (check ui-dashboard / ui-service-interaction sources if reachable).
7. **Release path** — run the workflows' logic locally where CI isn't
   available: build the image from a *clean* checkout (no local caches),
   boot it, and run the harness against it exactly as
   `evidence/r12-port-capture-manifest.json` claims was done; verify the
   manifest's sha256s against your own captures.

## Mandate C — the nine reconsideration conditions, one verdict each

Your report's §"Exact conditions to reconsider cutover" — verify each as
MET / PARTIALLY MET / NOT MET, empirically:

1. Java 21 image through the checked-in release path; both descriptors
   fixed; root descriptor/SBOM validated.
2. Published tenant lifecycle routes + parameter-driven seeding; note: the
   implementer resolved this via the `_tenant` **2.0** interface (ADR-012,
   D-17) rather than 1.2 parity — judge that decision on its merits, and
   run install/upgrade/purge through a real Okapi if the rig allows;
   otherwise state precisely what an Okapi-less verification leaves open.
3. 64-bit DTOs + atomic first-use generation, with adopted-boundary and
   concurrency regressions.
4. Full kiwt/listing/error contract implemented or explicitly governed with
   consumer evidence (REQ-022, D-19, D-22..D-25 are the claims).
5. Tenant-keyed, lifecycle-evicted caches; signing-key expiry/rotation and
   `kid` semantics (D-21); display-data identity (D-20).
6. Fresh-DDL default parity; adoption verification distinguishing business
   schema from Liquibase metadata.
7. Executable, fail-closed harness + runbook (canary, rollout, backup/
   restore, monitoring, abort/resume, timer, rollback, re-cutover). Execute
   the runbook's cutover + rollback phases on the rehearsal rig from the
   document alone — a step you cannot execute as written is a finding.
8. Semantic validation reproduced with machine-readable evidence — re-run
   `sdd validate --semantic --branch main-final` yourself and diff your
   verdict counts against `evidence/semantic-validation.json` (claim:
   0 errors / 0 warnings, PASS=238 / SUSPECT=0 / FAIL=0 / UNKNOWN=0);
   spot-check the corrected TRC-019/020/023 and REQ-005 AC5 against code.
9. This review's empirical matrix re-run **against the built release
   artifact** — that is this review itself: run your №1 probe set (and the
   harness `probes.tsv` superset) against the booted image, side-by-side
   with booted legacy wherever the probe is legacy-comparable.

## Deliverables

One review report (Markdown), containing:

1. **Closure matrix** — F-01..F-16 + issues 1/2, each CLOSED / PARTIALLY
   CLOSED / OPEN / REGRESSED with the reproduction you ran.
2. **Conditions table** — the nine reconsideration conditions, MET /
   PARTIALLY MET / NOT MET with evidence pointers.
3. **Findings register** — new findings only (numbering continues at
   **F-17**): `ID | Severity | What you observed | Reproduction (command +
   output excerpt) | Recommended action`. A deviation absent from D-1..D-25
   is at minimum Major. A committed evidence artifact you could not
   reproduce (manifest hash mismatch, semantic count mismatch) is at
   minimum Major.
4. **Coverage map** — probed vs unprobed, so "no finding" ≠ "verified".
5. **Dissent section** — unresolved disagreements, strongest form of each.
6. **Final recommendation** — go / conditional-go (exact conditions) /
   no-go for production cutover, plus the short must-fix-before-PR-merge
   list regardless of cutover timing.

## Acceptance bar for THIS review

The review itself fails if any of these are true: a finding was marked
CLOSED without re-running its original reproduction; the release image was
never built and booted from a clean checkout; the semantic run was not
re-executed; no reviewer attempted to find D-26; the runbook's rollback
phase was not walked; the previous report was treated as settled truth
instead of re-derived; or dissent was smoothed over instead of recorded.
An honest verdict here may well be GO — but only if it survives everything
above.
