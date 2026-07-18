# Task definition — as received and understood

This document records the task exactly as it was given to and understood by the
executing agent (Claude Code), including the standing rules that governed how
the work had to be performed and the mid-course directives that refined it.
The companion document, `completion-report.md`, provides the evidence that each
element below was fulfilled.

## 1. The mandate

**Rewrite `mod-service-interaction` — a legacy FOLIO backend module written in
Grails 6 / Groovy (living under `service/`) — as a Java Spring Boot FOLIO
module built on `folio-spring-support`, located at the repository root, such
that it is wire-compatible and data-lossless for a seamless production
cutover.**

Unpacking each requirement as understood:

- **Wire-compatible**: consumers of the legacy module (ui-dashboard,
  ui-service-interaction, other FOLIO modules calling `/servint/*`) must
  observe the same HTTP surface — same routes, same permission names, same
  request binding, same response JSON shapes, same status-code semantics. The
  acceptance bar was *empirical byte-level parity* wherever legacy behavior is
  well-defined, with every unavoidable or deliberate deviation explicitly
  enumerated and justified rather than silently introduced.
- **Data-lossless**: existing production tenants' data — one Postgres schema
  per tenant, populated over years by the Grails module — must be usable by
  the new module *as-is*: no export/import, no data-migration scripts, no
  destructive DDL. The new module adopts the legacy schemas in place.
- **Seamless production cutover**: operations must be able to switch a live
  installation from the legacy module to the port through the standard Okapi
  module-upgrade flow, with a verification procedure and a credible rollback
  path. Dependent modules and UI bundles must not require changes
  (ModuleDescriptor interface/permission parity).
- **The legacy module stays untouched** under `service/` — for reference,
  side-by-side verification, and rollback.

Reference repositories were provided as additional working directories for
architectural guidance: `mod-consortia-keycloak`, `mod-users-keycloak`
(examples of folio-spring-based FOLIO modules), and `folio-spring-support`
(the target platform library itself).

## 2. The methodology constraint: spec-first via SDD

The rewrite was not to be a direct code-to-code translation. The user mandated
a **spec-first workflow** governed by the project's SDD (Spec-Driven
Development) toolchain and its skill library (`.claude/skills/sdd-*`):

1. The legacy module's behavior is first reverse-engineered into a complete,
   validated specification universe under `specs/` (requirements with
   acceptance criteria, NFRs, decision records, OpenAPI contracts, data
   models, DTO mappings, architecture model, Gherkin behavior scenarios,
   exemptions).
2. The implementation is then written *against the specs* (API interfaces
   generated from `specs/api/*.yaml` unmodified).
3. **Every change to `specs/` must flow through formal `/sdd-*` Skill
   invocations** running the full governed session loop — no direct edits:
   - Phase A: baseline branch-graph load (`sdd validate --branch
     main-baseline`) + grounding reads;
   - Phase B: IntentPlan decomposition (confirmed autonomously per the
     autonomy rule below, journaled in narration);
   - Phase C: changeset open with a byte-identical baseline snapshot under
     `.sdd/changesets/<session-id>/baseline/`;
   - Phase D: journaled apply-in-place authoring with per-file V1 schema
     validation;
   - Phase E: full cross-file validation — **always including the semantic
     lane** (`sdd validate --semantic --branch main-final`), per explicit user
     instruction;
   - Phase F: atomic V3 commit, post-commit graph rebuild, `sdd graph diff
     main-baseline main-final`, a self-sufficient Markdown handoff at
     `.sdd/handoffs/<session-id>.md`, and narration closure.
4. Post-implementation, the traceability layer (`specs/traceability/`) links
   source symbols back to the spec elements they fulfill (via the
   `sdd-impl-trace` peer orchestrator).

## 3. Milestone structure

The work was organized into six milestones, each with its own acceptance bar:

| Milestone | Deliverable | Acceptance bar as understood |
|---|---|---|
| **M0** | Complete spec universe reverse-engineered from the legacy module | `sdd validate` clean (0 errors / 0 warnings), semantic lane PASS, every legacy behavior captured incl. fixture-level examples |
| **M1** | Target architecture as SDD decision records | ADRs covering stack, API generation, schema adoption, tenancy, federation transport, attestation, timers, descriptor parity — committed through governed sessions |
| **M2** | Scaffolded Java module | Builds (`mvn package`), generates the descriptor, boots, enables a tenant with the legacy-identical schema name |
| **M3** | Domain slices with behavior parity | All functional areas (number generators, dashboards + widgets + federation, RFC 8693 attestation, refdata/settings, seeding, admin) implemented; integration tests pin legacy wire fixtures; `mvn verify` green |
| **M4** | **Prove** production data migration | Boot the *real* legacy module, populate a tenant through its REST API, adopt the same database with the port, and demonstrate: adoption changesets MARK_RAN, schema untouched, data reads back identically on the wire |
| **M5** | Cutover package | Descriptor diff vs the legacy `ModuleDescriptor-template.json`, cutover documentation, the accumulated wire-compat deviation dossier, PR |

## 4. Standing operating rules

These rules governed the *manner* of execution throughout:

1. **Full autonomy** — work continuously without asking the user questions;
   resolve ambiguity from the code, the specs, and prior decisions.
2. **Round-based execution with compaction stops** — at the end of each work
   round, journal state into the scratchpad `session-state.md` and stop
   cleanly so the user can compact the conversation context. (Later
   overridden per-round by explicit "continue without compaction" messages.)
3. **Never git-commit autonomously** — the user decides commits. All work
   accumulates in the working tree on branch `feat/migration-01` until the
   user explicitly authorizes a commit.
4. **Full validation always includes the semantic lane** —
   `sdd validate --semantic --branch main-final`, never the structural lanes
   alone.
5. **User-global engineering rules** (from the user's `CLAUDE.md`): check
   current library documentation before implementation work against external
   APIs; Clean Code principles; token-efficient output; surgical changes —
   touch only what the task requires.

## 5. Mid-course directives (chronological)

These arrived during execution and refined the task:

1. **"Do not create a PR yet."** — received while M5 was in progress; the PR
   element of M5 was put behind an explicit user gate. Repeated later
   ("do not create a PR yet") — understood as a standing gate until revoked.
2. **"Please continue without compaction."** (three times) — override of
   standing rule 2 for those rounds: keep working in the same context instead
   of stopping for compaction.
3. **"Please rerun the failed commands and continue."** — re-execute the
   commands that had errored (they were harness artifacts: an invalid CLI
   flag and a wrong grep pattern), verify the true end-state, and proceed.
4. **"Run the semantic re-judge. LLM provider is up and running now."** — the
   semantic-judge provider had been down for part of the work (advisory
   UNKNOWN verdicts were journaled per an established degradation precedent);
   this directive triggered the mandatory re-judge, with any resulting real
   finding to be fixed through the governed loop.
5. **"Please commit all changes now."** — the explicit commit authorization
   per standing rule 3: commit the accumulated working tree (the PR gate
   remained in force).
6. **Create this pair of documents** — a task-definition record and a
   completion report with evidence.

## 6. Explicit non-goals / boundaries

- No modification of the legacy module under `service/`.
- No PR creation without explicit user go-ahead (still in force at the time
  of writing).
- No pushing to the remote (commit authorization ≠ push authorization).
- Legacy *defects* (endpoints that crash with 500s due to bugs) were NOT to
  be reproduced bug-for-bug; correct behavior with the deviation documented
  was the understood intent of "wire-compatible".
- `.sdd/` operational state (changesets, narration, handoffs) is session
  bookkeeping — kept out of the commit per the repository's `.gitignore`.
