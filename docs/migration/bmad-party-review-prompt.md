# Review mission brief — independent validation of the mod-service-interaction migration

> **How to run**: convene BMAD Party Mode with this file as the opening intent
> (`/bmad-party-mode --mode subagent` or `--mode agent-team` so every persona
> thinks independently; add `--non-interactive` for an unattended run to a
> natural close). Suggested room: Analyst, Architect, Dev, QA/Test Architect,
> PM, Tech Writer — open-cast a Security reviewer and an SRE when their
> dimensions come up.

---

## Mission

You are an **independent review team**. The Grails 6/Groovy FOLIO module
`mod-service-interaction` (legacy, untouched, under `service/`) has been
rewritten as a Java 21 / Spring Boot module at the repository root (commit
`d5ba8ea` on `feat/migration-01`), claiming **wire compatibility** and
**data-lossless in-place adoption** for a **seamless production cutover**.

Your job is to try to **break that claim**. You did not build this. You owe
the implementer nothing. A cutover of live production tenants rides on your
verdict: if you approve something broken, real libraries lose real data; if
you block something sound, demand evidence for the block. Deliver a
**go / conditional-go / no-go** recommendation for production cutover, backed
by findings you verified yourselves.

**Cardinal rule — empirical over textual.** This project's own history proves
static reading lies: four legacy-behavior claims derived from careful source
reading were falsified only when the real legacy module was booted and probed
(see `completion-report.md`, M4). Any finding you can verify by *running
something* must be verified by running it. A claim you could not reproduce is
reported as UNVERIFIABLE, never as confirmed.

**Independence rules**

- The implementer's documents (`task-definition.md`, `completion-report.md`)
  are *claims under review*, not evidence. Re-derive; don't quote back.
- Reproduce before judging; attach the command and output to every verdict.
- Preserve disagreement. If QA says no-go and Architect says go, the report
  carries both positions with their evidence — do not average.
- The repository is **read-only** for you. Scratch work, captures, and diffs
  go outside the repo. You change nothing, you commit nothing.

## Inputs

| Artifact | Role |
|---|---|
| `service/` | The legacy module — **ground truth** for all behavior |
| `src/`, `pom.xml`, `descriptors/` | The port under review |
| `specs/` | The governed spec universe incl. traceability (TRC-001..024) |
| `docs/migration/task-definition.md` | What the task was (claim) |
| `docs/migration/completion-report.md` | What was allegedly done (claim) |
| `docs/migration/cutover-runbook.md` | The operational procedure (claim) |
| `docs/migration/wire-compat-deviations.md` | Deviation register D-1..D-16 (claim of *completeness*) |
| `docs/migration/harness/` | populate/capture scripts for empirical rehearsal |
| `.sdd/handoffs/*.md` (local, gitignored — if present) | Session audit trail of all 11 governed spec sessions |
| `tools/testing/docker-compose*` | Postgres for the rehearsal rig (port 54321) |

Environment facts you will need: the port builds with Java 21 (`mvn -B
verify`); the legacy jar is `service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar`
and runs on JDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64`) with
`-Ddb.host/-Ddb.port/-Ddb.database/-Ddb.username/-Ddb.password` plus
`OKAPI_SERVICE_HOST`/`OKAPI_SERVICE_PORT` env (okapi register:false — no Okapi
needed); the port takes `DB_*` env vars and serves on 8082 when told to
(`--server.port`), legacy on 8081.

## Claims register — attack each one

Verdict per claim: **CONFIRMED** (you reproduced it) / **REFUTED** (evidence
attached) / **UNVERIFIABLE** (say exactly what blocked you).

- **C1 — Wire parity.** "13 endpoint states captured on identical data: 8
  byte-identical, 4 identical modulo undefined legacy listing order, 1 dead
  legacy endpoint." Re-run the rehearsal yourself: boot legacy against the
  `tools/testing` Postgres, `harness/populate.sh`, `harness/capture.sh`, swap
  modules on the same DB, capture again, diff. Then go *further than the
  harness*: probe endpoints, parameters, and error paths the 13 captures do
  NOT cover (kiwt filter/sort/paging matrices, malformed bodies, missing
  headers, unknown ids, duplicate names, stats envelopes).
- **C2 — Adoption is a no-op on legacy schemas.** All 14 Liquibase adoption
  changesets MARK_RAN against a legacy-populated schema; zero DDL executed;
  `information_schema` before/after diff empty; row counts unchanged.
- **C3 — DDL identity.** A fresh port-created tenant's DDL equals legacy DDL
  for every port-owned table — columns AND constraints *including names*.
  Diff the catalogs yourself (`information_schema` + `pg_constraint`).
- **C4 — Seeding idempotence.** Port tenant-enable on an adopted schema
  inserts nothing (legacy already seeded); on a fresh schema seeds exactly
  the legacy reference set (refdata values, 8 default generators, access
  values).
- **C5 — Descriptor parity.** All 4 provided interfaces, all 47 handler
  entries (methods, pathPatterns, permissionsRequired/Desired,
  modulePermissions), all 63 permissionSets content-identical to
  `service/src/main/okapi/ModuleDescriptor-template.json`. Machine-diff it
  independently — do not eyeball 27 KB of JSON.
- **C6 — Deviation register completeness.** D-1..D-16 claims to be *every*
  known deviation. Your mission: **find D-17.** Any behavioral difference you
  observe that is not in the register is a finding against C6 — this is the
  single most valuable thing this review can produce.
- **C7 — Spec validation.** `sdd validate --semantic --branch main-final`:
  structural 0 errors / 0 warnings, semantic 222/222 PASS. Re-run it.
- **C8 — Test suite.** `mvn -B verify` green: 32 integration tests + 3 unit.
  Re-run. Then judge what the tests *don't* pin (assertion depth, missing
  negative cases, ordering dependencies).
- **C9 — Traceability honesty.** Each TRC entry's code symbols exist and
  genuinely implement the ref'd requirement — spot-check by reading the code
  against the requirement's acceptance criteria, not just symbol existence.
- **C10 — Rollback safety.** The runbook claims rollback = re-enable legacy
  because `tenant_changelog` is untouched and port-written data stays
  legacy-valid. Rehearse it: cutover, write data through the port (new
  dashboard, consumed numbers, new setting), roll back, verify the legacy
  module reads and extends that data correctly.
- **C11 — Legacy untouched.** `git diff` scope of `d5ba8ea` contains nothing
  under `service/`.
- **C12 — Spec fidelity.** The Gherkin scenarios and requirement acceptance
  criteria describe what the *legacy module actually does* (booted, probed),
  not what the port conveniently implements.

## Review dimensions and lead voices

Every persona challenges everything, but each dimension needs an owner who
goes deep:

1. **Behavioral parity (QA lead, Dev seconds)** — C1, C6, C12. Domain-by-
   domain adversarial probing: number generation edge cases (checksum
   algorithms against independent implementations, `${current_year}` reset
   across a simulated year boundary, max-threshold warn/error boundaries,
   generation rollback-not-consumed semantics), dashboard access-matrix
   (every access level × every action × admin override), widget federation
   failure modes (provider down, malformed harvested definitions), refdata
   lookup domain matrix, partial-PUT null semantics against D-9.
2. **Data migration & persistence (Architect lead, SRE seconds)** — C2, C3,
   C4, C10. Also: Hibernate runtime schema expectations vs adopted DDL
   (would `ddl-auto validate` pass?), sequence/lock behavior under
   concurrency (two parallel `getNextNumber` on one sequence — pessimistic
   lock held?), TIMESTAMP timezone semantics on adopted vs fresh columns.
3. **Contract & integration surface (Analyst lead)** — C5 plus everything a
   descriptor can't show: permission *enforcement* in code vs declaration
   (does each handler actually check what it declares?), `_timer` semantics,
   tenant install/upgrade/disable/purge parameter handling vs legacy.
4. **Security (open-cast Security reviewer)** — attestation crypto (RS256
   key generation, storage of private keys in `db_key_pair`, JWT claim set vs
   RFC 8693 intent, expiry, kid handling), tenant isolation (the port fixed a
   legacy cross-tenant key-cache leak — verify the fix and hunt for siblings),
   kiwt filter parsing as an injection surface (filters land in JPA/SQL —
   prove parameterization), permission bypass on sub-routes.
5. **Code quality & maintainability (Dev lead, Architect seconds)** —
   MapStruct mapper correctness (the render-context variants: owner vs
   no-owner, id-stub, id-less definitions), transaction boundaries
   (`REQUIRES_NEW` usages, `UnexpectedRollbackException` handling), the
   KiwtListing engine vs the legacy kiwt feature matrix, error-handler
   completeness.
6. **Operational readiness (SRE, PM seconds)** — C10 plus the runbook read
   with hostile eyes: what does it NOT say? (multi-tenant scale of the
   per-tenant loop, monitoring during cutover, the Liquibase checksum caveat's
   blast radius, `_timer` re-registration after upgrade, container sizing
   from the launch descriptor).
7. **Documentation & spec governance (Tech Writer lead, PM seconds)** — C7,
   C9, C11 and: could an engineer who never saw this conversation execute the
   cutover from the docs alone? Is every dossier entry actionable? Do the 11
   SDD handoffs (if present) actually cover every spec delta in `git log`?

## Deliverables

One review report (Markdown), containing:

1. **Verdict table** — all 12 claims with CONFIRMED / REFUTED / UNVERIFIABLE
   and one-line evidence pointers.
2. **Findings register** — every finding as:
   `ID | Severity (Blocker / Major / Minor / Info) | Claim(s) affected |
   What you observed | How you reproduced it (command + output excerpt) |
   Recommended action`. A deviation absent from D-1..D-16 is at minimum
   Major.
3. **Coverage map** — what you probed vs what remains unprobed, so nobody
   mistakes "no finding" for "verified".
4. **Dissent section** — unresolved disagreements between reviewers, each
   with its strongest form.
5. **Final recommendation** — go / conditional-go (with the exact conditions)
   / no-go for production cutover, and a separate short list of what must be
   addressed before the PR merges regardless of cutover.

## Acceptance bar for THIS review

The review itself fails if any of these are true: a claim was marked
CONFIRMED without a reproduction artifact; the rehearsal rig was never
booted; no reviewer attempted to find D-17; the report contains only praise
(a competent adversarial review of 17k lines finds *something* — even
Minor/Info); or dissent was smoothed over instead of recorded.
