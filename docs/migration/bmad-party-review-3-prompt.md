# Review mission brief №3 — exhaustive final validation after the M7 remediation

> **How to run**: convene BMAD Party Mode with this file as the opening intent
> (`/bmad-party-mode --mode subagent` or `--mode agent-team` so every persona
> thinks independently; add `--non-interactive` for an unattended run to a
> natural close). Suggested room: Analyst, Architect, Dev, QA/Test Architect,
> PM, Tech Writer — open-cast a Security reviewer and an SRE when their
> dimensions come up. Reuse persona ownership from reviews №1 and №2 where it
> helps continuity, but every verdict must be re-derived, not remembered.

---

## Mission

You are the **independent review team** that has returned **NO-GO twice** on
the `mod-service-interaction` Grails→Spring Boot migration: at `d5ba8ea`
(review №1, `docs/migration/bmad-party-review-report.md`) and at `0d7dd33`
(review №2, `docs/migration/bmad-party-review-2-report.md` — 6 CLOSED,
7 PARTIALLY CLOSED, 1 OPEN, 2 REGRESSED, new findings F-17..F-25, 7 must-fix
items).

The implementer has since executed a second remediation program (M7, plan at
`docs/migration/remediation-plan-2.md`, workstreams R13–R21) landing
**12 commits on `feat/migration-01`** on top of `0d7dd33` (through
`950764a build(api): R21 …`), and claims: both REGRESSED items re-closed,
all nine F-17..F-25 findings closed, all seven must-fix items mapped to
commits and running pins, and final certification green — `mvn -B verify`
with 3 unit + 89 integration tests and 0 failures, semantic gate
PASS 241/0/0/0, an unmodified-harness both-sides run with exit 0, and a
real-Okapi lifecycle rehearsal matching the governed D-26 wire shapes.

Your job is to **try to break that claim — a third and final time**. This is
the last gate before the PR and production cutover, so the review is
**exhaustive**: the M7 delta is the priority attack surface, but your verdict
covers the *whole* migration, and anything unprobed must be named as
unprobed. You owe the implementer nothing, and you owe your own two previous
reports nothing either: a finding the remediation proves wrong gets retracted
with evidence, exactly as an unfixed finding gets re-confirmed with evidence.
Deliver **go / conditional-go / no-go**, backed by findings you verified
yourselves.

**Cardinal rule — empirical over textual.** This project's history now
proves it three times over: four legacy-behavior claims from M0–M3 fell only
when the real legacy module was booted (M4); the implementer's own R12
certification falsified a dossier claim that had never been exercised (D-2);
and your review №2 proved the harness's *legacy oracle itself* had never
been re-run after hardening — after which the implementer's R13
discriminating re-runs showed even the review's own two 201 observations
were the fresh-path half of a state-dependent 201-fresh/500-duplicate
behavior. Nobody's untested claim has survived contact with the booted
module — including yours. Any finding you can verify by *running something*
must be verified by running it. A claim you could not reproduce is
UNVERIFIABLE, never confirmed.

**Independence rules** (unchanged)

- Implementer documents — `completion-report.md` (including its new
  "M7 remediation outcome" section), both remediation plans, the dossier,
  the runbook, `.sdd/handoffs/` — are *claims under review*, not evidence.
- Reproduce before judging; attach command + output to every verdict.
- Preserve disagreement; the report carries both positions.
- The repository is **read-only** for you. Scratch work, captures, diffs,
  and any mutation experiments (see the drift-gate test below) happen on
  throwaway copies outside the repo. You change nothing, you commit nothing;
  your report is stored outside the repository.

## Inputs

| Artifact | Role |
|---|---|
| `docs/migration/bmad-party-review-2-report.md` | Your review №2 — closure matrix + F-17..F-25 + 7 must-fix items under closure test |
| `docs/migration/remediation-plan-2.md` | The M7 program (claim) |
| `git log 0d7dd33..HEAD` (12 commits) | The remediation delta — read it commit-by-commit |
| `service/` | The legacy module — **ground truth** for all behavior |
| `src/`, `pom.xml`, `descriptors/`, `Dockerfile`, `.github/workflows/`, `Jenkinsfile` | The port and its release path under review |
| `specs/` | Governed spec universe — 165 files; REQ-001..022, ADR-001..012, TRC-001..024 |
| `docs/migration/wire-compat-deviations.md` | Deviation register, now **D-1..D-31** (claim of completeness) |
| `docs/migration/harness/` | Frozen harness: `probes.tsv` (28 probes), `populate.sh`, `capture.sh`, `diff-runs.sh`, `deviation-allowlist.tsv` (D-1..D-31), `rollout.sh` |
| `docs/migration/cutover-runbook.md` | Operational procedure; Phase 4 now delegates to `rollout.sh` |
| `docs/migration/evidence/r13-legacy/` | Legacy-oracle re-establishment: rig identification, ×2 capture runs, directed-probe raw bodies, `r13b-populated-schema.sql` adoption fixture |
| `docs/migration/evidence/r18/` | Both-sides harness bundle: manifests with per-body sha256, diff-runs output, digests, transcript, semantic verdict summary |
| `docs/migration/evidence/r20-rehearsal/` | Real-Okapi lifecycle rehearsal: verbatim `/_/tenant` bodies, psql proofs, proxy logs, Okapi identification |
| `tools/testing/docker-compose*` | Postgres for the rehearsal rig |

Environment facts: port builds with Java 21 (`mvn -B verify`; suite claims
**89 integration + 3 unit**); the release image builds from the root
`Dockerfile` and serves 8081 with health at `/admin/health`; legacy jar
`service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar` (sha256
`abb37211…c57`) runs on JDK 17. A rehearsal rig may still be running on this
machine (legacy on :8080, `testing_pg` Postgres on :54321 with tenants
r13a/r13b/r18l/r18p, local image `mod-service-interaction:r18`): treat it as
a **convenience, never as evidence** — any verdict rides on state you built
yourself, and the `r18` image predates HEAD (R21 changed `pom.xml`), so
**rebuild the image before any image-based verdict**. The semantic lane
needs the local LLM proxy (`sdd.config.yaml#/semantic/model`: base_url
`http://127.0.0.1:54001/v1`, `OPENAI_API_KEY` in env); if it is down, get it
restarted rather than reporting UNVERIFIABLE.

## Mandate A — closure matrix: every review №2 verdict re-tested

For **each** of: F-02 and F-11 (REGRESSED → claimed re-closed), F-17..F-25
(new → claimed closed), and the review №2 PARTIALLY CLOSED / OPEN residue
(F-04, F-05, F-10, F-12, F-13, F-14, F-15, F-16) — verdict:

- **CLOSED** — you re-ran the finding's *original reproduction* (as recorded
  in your report) against HEAD and it no longer reproduces, AND the pinned
  regression the implementer claims (IT, harness probe, script gate)
  actually exists, actually runs, and actually asserts the failure mode.
  Name it.
- **PARTIALLY CLOSED** — the headline reproduction is fixed but a stated
  sub-case, edge, or the pinning evidence is missing/weaker than claimed.
- **OPEN** — still reproduces. Attach the reproduction.
- **REGRESSED** — the fix broke something else. Attach both.

Verify against the implementer's closure table in `completion-report.md`
§"M7 remediation outcome" — its commit hashes and IT names are leads, not
proof. Spot-checks that MUST happen:

- **F-17**: re-run your 20-client `getNextNumber` burst against the rebuilt
  release image with the default 10-connection pool — the claim is 20×200
  with unique gapless values, excess requests queuing on Hikari.
- **F-02/F-18 (D-26)**: POST the Okapi-shaped disable body yourself
  (`{"module_from":"…","purge":false}`) and verify 204, `databasechangelog`
  frozen, deleted seed data staying deleted. Then stand up a **real Okapi
  yourself** and drive install → upgrade → disable → re-enable → purge,
  capturing the actual `/_/tenant` bodies (the implementer's rehearsal used
  `folioorg/okapi:latest` = 7.0.6 while the design pin cites `TenantManager`
  at commit `dd321ba` — verify the wire shapes hold on the Okapi version you
  choose, and flag any version where they would not). Do not accept
  `evidence/r20-rehearsal/` without reproducing it.
- **F-11/F-19/F-20/F-21**: run the **unmodified** checked-in harness
  end-to-end on both sides — populate → capture legacy → capture port →
  `diff-runs.sh` — and require exit 0 with zero non-allowlisted divergences.
  Re-run the six directed F-21 probes raw against booted legacy AND port and
  check each against its registered D-number disposition (D-28..D-31).
- **F-22**: re-verify `evidence/r18/` **independently** — recompute every
  advertised sha256 from the committed bodies, replay a sample of manifest
  rows against modules you booted, and confirm the semantic verdict summary
  matches a semantic run you executed.
- **F-23**: run `WidgetCacheCapacityIT`, then push past it on your own
  terms (e.g. 2 000 simulated tenants; watch heap and the
  `widget-definition-tenant-cache` Micrometer gauges).
- **F-24**: execute `rollout.sh` yourself — `bash -n`, DRY_RUN, and a real
  ≥2-tenant wave on a rig you populated; force a mid-wave failure and verify
  the append-only ledger, stop conditions, and automatic rollback behave as
  documented.
- **F-25**: **mutation-test the drift gate** on a throwaway copy of the
  repo: delete one controller override → the build must fail to compile;
  add a new operation to one spec → the build must fail to compile. A drift
  gate you did not watch bite is not verified.
- **Adoption (must-fix 6)**: run `AdoptedSchemaUpgradeIT` and
  `FreshDdlCatalogIT`; additionally restore `r13b-populated-schema.sql`
  yourself and drive the upgrade over the wire, checking the
  `EXECUTED=0 ∧ MARK_RAN>0` gate and row survival independently.

## Mandate B — fresh adversarial sweep of the M7 delta

M7 added new production code, new scripts, and ~1 500 lines of new tests.
New code means new defects; your №2 findings say nothing about it. Hunt for
**D-32** — a behavioral difference absent from D-1..D-31 — and for defects
in the new machinery. Priority attack surface:

1. **R14 transaction shape.** Controllers dropped class-level
   `@Transactional` for per-method annotations, with `getNextNumber`,
   `resetYearSequences`, `editUserDashboards`, `editDashboardUsers`
   deliberately bare and a `TransactionTemplate` re-list in
   `DashboardsController`. Attack: lazy-initialization outside a
   transaction on any render path; behavior of the remaining class-level
   controllers under pool exhaustion; interleavings of
   generation + purge + year-reset.
2. **R15 disable interception.** `isOkapiDisableShape` =
   `module_from` present ∧ `module_to` blank ∧ `purge == FALSE`. Attack the
   complement: a blank-`module_to` body that *omits* `purge` binds
   `purge=true` (DTO default) and is routed as a **purge job** — the
   dossier calls this framework-stock and Okapi-unreachable; judge whether
   that footgun is acceptably governed or a destructive defect for
   hand-crafted calls. Also: whitespace/case variants, concurrent disable
   vs in-flight tenant requests, disable of a never-enabled tenant.
3. **R16 parser semantics.** Empty-RHS now throws `DroppedClauseException`
   with **per-filters-param** drop granularity. Verify against booted
   legacy that dropping the *whole* filters parameter (not just the clause)
   matches legacy behavior for compound expressions — e.g.
   `a==&&code==alpha`, `(code==alpha||prefix==)` — and that the
   `Boolean.valueOf` coercion, the backslash escape delta (D-29), and the
   10 k-filter container-level 400 (D-31) hold exactly as registered.
4. **R17 Caffeine cache.** `expireAfterAccess(30m)` + `maximumSize(500)`:
   size-eviction of an *active* tenant mid-request; `synchronized` fetch
   racing eviction; correctness of `cachedTenantCount()`'s `cleanUp()`
   probe; whether metrics registration survives context refresh; isolation
   ITs still green under eviction pressure.
5. **R20 rollout.sh + rehearsal.** Beyond the F-24 spot-check: ledger
   integrity across abort → resume; wave-loop behavior when a tenant is
   mid-upgrade at interrupt; the rendered Okapi rollback command's
   correctness against the Okapi version you stood up.
6. **R21 codegen flip.** `skipDefaultInterface=true` on all six executions:
   diff the served route table pre/post flip (undeclared-method semantics,
   405s, `/dashboard/definitions` off-prefix route); confirm zero runtime
   behavior change beyond compile-time enforcement.
7. **Evidence forensics.** `evidence/r13-legacy/`, `/r18/`, and
   `/r20-rehearsal/` claim third-party re-verifiability. Test that claim
   literally: pick rows at random, recompute, replay. Any hash that does
   not reproduce, any capture whose provenance cannot be established from
   the bundle alone, is at minimum Major.

## Mandate C — the seven must-fix items, one verdict each

Your review №2 §"Must fix before PR merge" — verify each as MET /
PARTIALLY MET / NOT MET, empirically, against the implementer's must-fix
mapping table in the completion report:

1. Nested-transaction pool-starvation removal + pool-size coverage.
2. Real Okapi 2.0 disable corrected and tested; ADR/REQ/TRC/D-17 updated;
   D-26 registered.
3. D-2 corrected; D-27 and parser/error deviations registered; unmodified
   harness re-run.
4. Executable, append-safe rollout/abort/rollback automation; descriptor
   path and year handling fixed.
5. Auditable legacy+port captures, diffs, artifact digests, semantic
   verdict evidence retained.
6. Populated adopted-schema regression + fresh-DDL catalog assertion.
7. Per-tenant definition cache bounded and measured + the drift gate.

## Mandate D — the nine review №1 reconsideration conditions, final state

This is the final gate: restate each of the nine conditions from review №1
with its terminal verdict (MET / PARTIALLY MET / NOT MET) and one-line
evidence pointer, so the cutover decision-maker sees the complete arc in
one table. Conditions 2 (real-Okapi lifecycle) and 7 (executable
harness+runbook) were the ones review №2 could not close — they now have
in-repo claims (`evidence/r20-rehearsal/`, `rollout.sh`); judge them on
your own reproductions from Mandates A and B.

## Mandate E — exhaustive whole-project validation

The delta mandates above do not exhaust the mission. Before the verdict,
sweep the whole system once more, prioritizing what no prior review probed:

- **Wire parity**: the 28-probe harness plus your own exploratory probing —
  undeclared paths, methods on declared paths, header-variant behavior
  (missing/garbage `X-Okapi-*`), content-type edge cases.
- **Security**: attestation JWTs (alg confusion, `kid` handling, tenant
  claim integrity, expiry); injection through filter values, sort
  expressions, match properties into JPA Criteria; secrets or SQL text in
  error bodies and logs; the signing-key cache under cross-tenant access.
- **Data continuity**: the full runbook walked cold — backup, canary wave,
  rollback (Phase R) executed on a populated rig, re-cutover after
  rollback.
- **Scale/performance**: pool-size bursts on the hot paths beyond
  `getNextNumber`; listing endpoints under pathological-but-legal filters;
  multi-hundred-tenant enable storms.
- **Spec governance**: re-run `sdd validate --semantic --branch main-final`
  and diff your counts against the claim (165 files / 0 errors /
  PASS 241/0/0/0); spot-check that TRC code symbols exist at HEAD and that
  the R15/R16 spec rewrites match what the code actually does.
- **Release path**: clean-checkout build → image → boot → harness, per the
  checked-in workflows' logic; SBOM and descriptor validation.
- Anything you probe and find sound belongs in the coverage map as
  *verified*; anything you skip belongs there as *unprobed*. Silence is
  not coverage.

## Deliverables

One review report (Markdown, stored outside the repository), containing:

1. **Closure matrix** — every review №2 finding and residue item, each
   CLOSED / PARTIALLY CLOSED / OPEN / REGRESSED with the reproduction you
   ran.
2. **Must-fix table** — the seven items, MET / PARTIALLY MET / NOT MET with
   evidence pointers.
3. **Conditions table** — the nine review №1 reconsideration conditions,
   final state.
4. **Findings register** — new findings only (numbering continues at
   **F-26**): `ID | Severity | What you observed | Reproduction (command +
   output excerpt) | Recommended action`. A behavioral deviation absent
   from D-1..D-31 is at minimum Major. A committed evidence artifact you
   could not independently reproduce is at minimum Major.
5. **Coverage map** — verified vs unprobed, explicitly.
6. **Dissent section** — unresolved disagreements, strongest form of each.
7. **Final recommendation** — go / conditional-go (exact conditions) /
   no-go for production cutover, plus the must-fix-before-PR-merge list
   regardless of cutover timing.

## Acceptance bar for THIS review

The review itself fails if any of these are true: a finding was marked
CLOSED without re-running its original reproduction; no reviewer stood up a
real Okapi and drove the lifecycle; the unmodified harness was not re-run
end-to-end on both sides; the committed evidence hashes were not
independently recomputed; the drift gate was never watched to fail a
mutated build; `rollout.sh` was never executed against a populated rig;
no reviewer hunted D-32; the release image was not rebuilt from HEAD before
image-based verdicts; a previous report of yours was treated as settled
truth instead of re-derived; or dissent was smoothed over instead of
recorded. An honest verdict here may well be **GO** — but only if it
survives everything above.
