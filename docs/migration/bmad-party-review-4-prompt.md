# Review mission brief №4 — exhaustive final validation after the M8 remediation

> **How to run**: convene BMAD Party Mode with this file as the opening intent
> (`/bmad-party-mode --mode subagent` or `--mode agent-team` so every persona
> thinks independently; add `--non-interactive` for an unattended run to a
> natural close). Suggested room: Analyst, Architect, Dev, QA/Test Architect,
> PM, Tech Writer — open-cast a Security reviewer and an SRE when their
> dimensions come up. Reuse persona ownership from reviews №1–№3 where it
> helps continuity, but every verdict must be re-derived, not remembered.

---

## Mission

You are the **independent review team** that has returned **NO-GO three
times** on the `mod-service-interaction` Grails→Spring Boot migration: at
`d5ba8ea` (review №1), at `0d7dd33` (review №2), and at `950764a` (review №3,
`docs/migration/bmad-party-review-3-report.md` — every core migration claim
independently reproduced E1–E12, 10 of 16 review-№2 items CLOSED, but
1 Blocker F-27, 8 Major F-26/F-28..F-34, 2 Minor F-35/F-36, F-21 REGRESSED
via F-26, and residues on F-12/F-13/F-16/F-22/F-24).

The implementer has since executed a third remediation program (M8, plan at
`docs/migration/remediation-plan-3.md`, workstreams R22–R29 + R-CERT) landing
**12 commits on `feat/migration-01`** on top of `950764a` (through
`c2a794d docs(cert): M8 R-CERT …`), and claims: every review №3 finding
closed, all five of your merge-gate items closed, and a certification round
that rebuilt everything from cert HEAD and re-proved it empirically —
`mvn -B verify` with 3 unit + **95 integration tests** and 0 failures;
semantic gate 165 files / 0 errors / **PASS 245/0/0/0** with the full
per-finding corpus retained; the unmodified 35-probe harness green on both
sides with the adoption diff at **0 divergences**; a **real-Okapi rollout
re-rehearsal** of the rewritten `rollout.sh` with a control wave, a forced
mid-wave failure, an automatic authenticated rollback with inverse routing
re-verify, and a resume run whose predecessor's evidence stayed
byte-identical; the explicit-purge tenant contract re-verified over the
wire; and an evidence-forensics pass in which every previously documented
hash recomputed clean (`docs/migration/evidence/rcert/`).

Your job is to **try to break that claim — a fourth time**. This is the last
gate before the PR and production cutover. The M8 delta is the priority
attack surface, but your verdict covers the *whole* migration, and anything
unprobed must be named as unprobed. You owe the implementer nothing, and you
owe your own three previous reports nothing either: a finding the
remediation proves wrong gets retracted with evidence, exactly as an unfixed
finding gets re-confirmed with evidence. Deliver **go / conditional-go /
no-go**, backed by findings you verified yourselves.

**Cardinal rule — empirical over textual.** The project's history now proves
it four times over, and the fourth instance is the sharpest: review №3's
F-26 was confirmed by everyone — implementer included — as "legacy drops the
empty clause", yet when R22 finally put a 10-probe matrix against the booted
legacy module, the *actual* legacy behavior turned out to be **greedy value
absorption**, something neither the implementer's spec, nor the port, nor
your own review had imagined. Nobody's untested claim has survived contact
with the booted module — including yours, twice now. Any finding you can
verify by *running something* must be verified by running it. A claim you
could not reproduce is UNVERIFIABLE, never confirmed.

**Honesty audit — new in this review.** The M8 completion annex openly
states residuals: bit-for-bit row-content identity asserted by no IT; the
live failure-injection rehearsal exercised exactly one failure class (the
adoption catalog gate); the cert-HEAD semantic corpus is 100% cache-replayed
verdicts. Stated residuals are not findings — but an **unstated** residual
is, and so is a stated one whose framing understates its risk. Audit the
residuals list for completeness and accuracy as its own line of attack.

**Independence rules** (unchanged)

- Implementer documents — `completion-report.md` (including its
  "M8 remediation outcome" annex), all three remediation plans, the dossier,
  the runbook, `.sdd/handoffs/` — are *claims under review*, not evidence.
- Reproduce before judging; attach command + output to every verdict.
- Preserve disagreement; the report carries both positions.
- The repository is **read-only** for you, with one exception: your report
  is stored as `docs/migration/bmad-party-review-4-report.md` and is the
  **only repository write** you make. Scratch work, captures, diffs, and all
  mutation experiments happen on throwaway copies outside the repo.

## Inputs

| Artifact | Role |
|---|---|
| `docs/migration/bmad-party-review-3-report.md` | Your review №3 — findings F-26..F-36, residues, 5 merge-gate items under closure test |
| `docs/migration/remediation-plan-3.md` | The M8 program (claim) |
| `git log 950764a..HEAD` (12 commits) | The remediation delta — read it commit-by-commit |
| `service/` | The legacy module — **ground truth** for all behavior |
| `src/`, `pom.xml`, `descriptors/`, `Dockerfile`, `.github/workflows/`, `Jenkinsfile` | The port and its release path under review |
| `specs/` | Governed spec universe — 165 files; REQ-001..022 (REQ-020 AC6 and REQ-022 AC1/AC6 rewritten in M8), ADR-001..012 (ADR-012 amended), TRC-001..024 (TRC-023 corrected) |
| `docs/migration/wire-compat-deviations.md` | Deviation register, now **D-1..D-32** (claim of completeness) |
| `docs/migration/harness/` | Frozen harness: `probes.tsv` (**35** probes incl. 7 `r22-*` rows), `populate.sh`, `capture.sh`, `diff-runs.sh`, `deviation-allowlist.tsv` (D-1..D-32), **`rollout.sh` rewritten in R23** (MODE=okapi default, immutable evidence, 8-col ledger) |
| `docs/migration/cutover-runbook.md` | Phases 4/5/6 rewritten in R24 (checked cutover, timer verification without the false abort, aligned rollback) |
| `docs/migration/evidence/r22-legacy-compound/` | The R22 legacy oracle for compound empty-RHS semantics (10 probes, created-row discriminators) |
| `docs/migration/evidence/r23-dryrun-probes/` | R23 config-gate probe matrix (rejection paths, DRY_RUN) |
| `docs/migration/evidence/r26-drift-probe/` | Tenant-surface gate bite-proof (extra-op + status mutations → build failures) |
| `docs/migration/evidence/semantic/` + `semantic/rcert/` | Full 245-finding verdict corpora (R27 baseline + cert-HEAD recapture) |
| `docs/migration/evidence/rcert/` | The R-CERT bundle: `harness/` (3×35 probes + diff), `lifecycle/` (explicit-purge wire contract), `okapi-rollout/` (real-Okapi control wave / forced failure / verified rollback / resume, wire taps, ledger), `forensics.md` |
| `docs/migration/evidence/r13-legacy/`, `/r18/`, `/r20-rehearsal/` | Prior evidence bundles — r20's `proxy.py` is now the R27-hardened tap; r18/r20 carry new provenance statements |
| `tools/testing/docker-compose*` | Postgres for the rehearsal rig |

Environment facts: port builds with Java 21 (`mvn -B verify`; suite claims
**95 integration + 3 unit**); the release image builds from the root
`Dockerfile`, serves 8081, health at `/admin/health`; legacy jar
`service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar` (sha256
`abb37211…c57`) runs on JDK 17. A rehearsal rig may still be running on this
machine (legacy on :8080, a `rcert-port` container on :8081, `testing_pg`
Postgres on :54321 with tenants r13a/r13b/r18l/r18p/r22o/rcl/rcp/rcw1a/
rcw1b/rcw2ok/rcw2bad, local images `mod-service-interaction:{r18,rcert}`):
treat it as a **convenience, never as evidence** — any verdict rides on
state you built yourself, and you **rebuild the image from HEAD before any
image-based verdict**. Operational notes that will save you time: Okapi
7.0.6 dev mode parses any supplied `X-Okapi-Token` and 400-rejects non-JWT
shapes (use a JWT-shaped dummy or omit the header); the host has no `psql`
client (the implementer used a docker-exec shim into `testing_pg`); the
semantic lane needs the local LLM proxy (`sdd.config.yaml#/semantic/model`:
base_url `http://127.0.0.1:54001/v1`, `OPENAI_API_KEY` in env) — if it is
down, get it restarted rather than reporting UNVERIFIABLE.

## Mandate A — closure matrix: every review №3 verdict re-tested

For **each** of F-26..F-36 and the review №3 residues (F-12, F-13, F-16,
F-22, F-24) — verdict CLOSED / PARTIALLY CLOSED / OPEN / REGRESSED under the
same definitions as before: CLOSED requires re-running the finding's
*original reproduction* against HEAD **and** confirming the claimed pin
exists, runs, and asserts the failure mode. Verify against the closure table
in `completion-report.md` §"M8 remediation outcome" — its commit hashes and
IT names are leads, not proof. Spot-checks that MUST happen:

- **F-26 (and the F-21 regression)**: re-derive the legacy compound-empty-RHS
  oracle **yourself** against booted legacy — your own review's six probes
  plus R22's ten (`evidence/r22-legacy-compound/` documents the
  discriminating created-row technique; reproduce it, don't trust it). Then
  check the port: REQ-022 AC1/AC6 vs `KiwtFilterParser`'s absorption
  pre-pass vs actual wire behavior, `KiwtListingGrammarIT` (claims 15/15),
  the 7 `r22-*` harness rows on BOTH sides, and every D-32 disposition in
  the allowlist. Greedy absorption is a strange contract — probe around its
  edges (below, Mandate B.1) for a D-33.
- **F-27/F-28/F-29/F-30/F-35 (the rollout complex)**: execute the rewritten
  `rollout.sh` yourself against a **real Okapi you stood up**: `bash -n`,
  DRY_RUN incl. malformed-config rejection, then a real ≥2-tenant wave in
  MODE=okapi on tenants you populated — routing verified per tenant, smoke
  through Okapi, `complete` rows carrying routing state. Then **force your
  own mid-wave failure, deliberately picking a DIFFERENT failure class than
  the implementer's** (their live rehearsal exercised the adoption catalog
  gate; the stated residual admits other classes ran only as dry-run
  probes). Candidates: kill/pause the port container mid-install
  (transport-error reconciliation), break a smoke probe, make Okapi
  unreachable between enable and verify, and — separately — sabotage the
  *rollback* itself to watch the failed-rollback HARD_STOP fire. Verify
  with your own hashes that the failed run's evidence is byte-intact after
  the retry, that run directories are refused for reuse, that the ledger
  only ever appends, and that no credential value appears in any evidence
  file (the manual commands must reference `$OKAPI_TOKEN`).
- **F-31**: walk runbook Phase 5 against your Okapi: timer registered in
  `/_/proxy/tenants/<T>/timers`, observed execution semantics, and the
  manual POST through Okapi documented as EXPECTED-404 — confirm the
  false-abort instruction is gone and the replacement is operationally
  sound.
- **F-32**: over the wire against the rebuilt image: `{module_from}` alone,
  `{}`, and `{module_from, purge: null}` must each 400 with code
  `purge.not.explicit` and provably zero side effects (schema + changelog
  state before/after); explicit `purge:false` disable side-effect-free;
  explicit `purge:true` the only destructive path. Confirm the spec side
  (REQ-020 AC6, `servint-tenant.yaml` 400 response, ADR-012 amendment) says
  what the code does. Then attack the mechanism itself (Mandate B.2).
- **F-33**: mutation-test the tenant-surface gate on a throwaway copy with
  **your own mutations**, not the committed fixtures: an undeclared served
  route, a declared-unserved route, a status change — each must fail the
  build. Then judge what the gate does NOT see (Mandate B.4).
- **F-34**: audit the hardened `proxy.py` (sole copy at
  `evidence/r20-rehearsal/proxy.py`): code-review the redaction set and
  ordering, then probe it live — sensitive headers redacted-with-hash in
  persisted artifacts while the upstream receives originals, atomic capture
  writes, startup sink preflight, and the unwritable-sink case where the
  client must still receive the true upstream response alongside a loud
  `capture-failed` marker. Sweep ALL retained evidence for
  credential-shaped values yourself; confirm the one historical redaction
  (`proxy-legacy.jsonl` record 1) and its recomputed hashes.
- **F-36**: scrape `cache.size`/`cache.evictions` over HTTP on the running
  image (`/admin/metrics/{name}?tag=cache:widget-definition-tenant-cache`)
  and re-run `WidgetCacheCapacityIT`. Then judge the exposure surface
  itself (Mandate B.6).
- **F-22 residue (provenance + corpus)**: recompute every hash the bundles
  document — `evidence/rcert/forensics.md` claims an all-green recompute;
  redo it independently (r18 manifests 84/84, r20 post-redaction files,
  semantic baselines, the descriptor determinism claim). Test the
  provenance statements literally: what r18/r20 say is verifiable from the
  bundle alone must verify from the bundle alone; what they say needs a
  rebuild must match your own rebuild+rerun.
- **F-13 residue (TRC-023)**: read TRC-023 at HEAD against the actual
  assertions of `AdoptedSchemaUpgradeIT` and `FreshDdlCatalogIT` —
  method by method. The corrected text must neither overclaim nor
  underclaim; the "bit-for-bit unasserted" residual must be literally true.
  Spot-check the rest of the traceability layer for the same defect class.
- **F-12 / F-16 / F-24 residues**: re-test each against your review №3
  wording of what remained open; map each to where (or whether) M8 closed
  it, and say so explicitly — silence on a residue is a review failure.

## Mandate B — fresh adversarial sweep of the M8 delta

M8 added a parser pre-pass, a request-body advice, ~1 000 lines of new bash,
a runtime contract gate, proxy hardening, and an actuator exposure change.
New code means new defects; your №3 findings say nothing about it. Hunt for
**D-33** — a behavioral difference absent from D-1..D-32 — and for defects
in the new machinery. Numbering for new findings continues at **F-37**.
Priority attack surface:

1. **R22 absorption pre-pass** (`KiwtFilterParser.absorbEmptyRightSides` +
   the reordered validity checks in `KiwtListing`). Attack with booted
   legacy as oracle: multiple empty leaves in one parameter, empty leaves in
   *nested* parens, NOT-around-empty (`!(prefix==)` — legacy 500s; what
   does the port do and is it registered?), absorption interacting with
   escaped `\&&`/`\||` sequences, raw-spelling retention vs unescape
   ordering, absorption colliding with `sort`/`stats`/`match` parameters,
   dotted paths and unknown properties on BOTH sides of the drop-vs-absorb
   boundary. The greedy-absorption contract is subtle enough that a D-33 in
   its edge cases is your single most likely fresh find.
2. **R25 `TenantPurgeFlagAdvice`** (a `RequestBodyAdviceAdapter` that
   buffers the raw body to detect whether `purge` was present on the wire,
   then post-binding resets the bound default). Attack: malformed JSON must
   still travel the D-22 error path unchanged; charset and Content-Length
   edge cases; chunked bodies; a body where `purge` appears in a nested or
   duplicated key; whether the advice fires only for `TenantAttributes`
   (other endpoints' bodies must be untouched); concurrent tenant
   operations; and the interception ordering vs the controller's
   disable-shape gate and folio-spring's own `isDisableJob` unboxing.
3. **R23 `rollout.sh`** (rewritten, ~1 000 lines of bash). Beyond the
   Mandate A execution: CSV ledger escaping under hostile detail strings
   (commas, quotes, newlines in failure messages); run-id uniqueness under
   rapid retries; the transport-error *reconciliation* logic — force the
   ambiguous branch and check the conservative `ENABLED=true` path rolls
   back a tenant that was never actually enabled without damage; MODE=direct
   semantics (rows must say `verified-direct`, never `complete`); the
   pre-R23 6-column ledger append path; tenant normalization at the
   arg-vs-file boundary; DRY_RUN's fidelity to the real command set.
4. **R26 `TenantContractCompletenessIT`**. It compares served
   routes/methods and pinned status codes two ways. What it cannot see:
   request/response schema drift, parameter drift, header semantics. Build
   a drift it misses on a throwaway copy, then judge whether the README's
   narrowed claim states the gate's limits honestly.
5. **R27 `proxy.py` hardening**. Redaction set completeness: tokens in
   query strings, non-canonical header casings, multi-value headers,
   `Set-Cookie` on responses (are response headers persisted anywhere?);
   JSONL integrity under concurrent requests; the atomicity claim under a
   crash between tmp-write and rename.
6. **R29 exposure surface**. `management.endpoints.web.exposure.include:
   health, loggers, metrics` on `/admin`, served by the module port to
   anyone with network reach, with no Okapi routing declared for `/admin/*`
   (deliberately infrastructure-local). Note that Spring Boot's `loggers`
   endpoint accepts **POST** to change log levels at runtime. Judge the
   operational risk posture of the full exposed set against FOLIO
   deployment reality (module ports are cluster-internal), and whether the
   runbook/README document the surface accurately. If you judge it a
   defect, it is a finding; if acceptable, say so explicitly with the
   deployment assumption named.
7. **Evidence forensics on the NEW bundles** (`rcert/*`, `semantic/rcert/`,
   `r22-legacy-compound/`, `r23-dryrun-probes/`, `r26-drift-probe/`): the
   third-party-reproducibility claim, tested literally — pick rows at
   random, recompute, replay. Any hash that does not reproduce, any capture
   whose provenance cannot be established from the bundle alone, is at
   minimum Major.
8. **Semantic corpus faithfulness.** Both retained corpora show
   `verdict_source: cache` on all 245 findings. Force a **live** re-judge
   of a random sample (run the semantic lane on a throwaway copy with the
   cache cleared or bypassed) and compare verdicts and rationales against
   the cached corpus. A cache that cannot be revalidated live is a
   provenance gap; a live verdict that flips a cached PASS is a finding.

## Mandate C — the five review №3 merge-gate items, one verdict each

Your review №3 §"Final recommendation and merge gate" — verify each as
MET / PARTIALLY MET / NOT MET, empirically, against the implementer's
merge-gate mapping in the completion annex:

1. F-27/F-28/F-29/F-30/F-31/F-35 resolved AND the corrected tool rerun
   through real Okapi with immutable evidence, forced failure, authenticated
   rollback, resume, and routing verification.
2. F-26 fixed or explicitly governed as D-32, with all
   compound/dotted/unknown-property cases pinned in the IT and harness
   against booted legacy.
3. F-32 resolved by an explicit-destructive-intent contract.
4. The tenant API under an explicit drift/compatibility gate (F-33).
5. Retained-evidence claim and TRC-023 corrected; exact semantic verdict
   evidence and sufficient artifact provenance retained without secrets
   (F-22/F-13/F-34).

Item by item, state what YOU ran to reach the verdict — the implementer's
R-CERT bundle is their execution of your gate; your verdict requires yours.

## Mandate D — the terminal arc, one consolidated table

This is the fourth and intended-final review. Give the cutover
decision-maker the complete arc in one place: the nine review №1
reconsideration conditions, the seven review №2 must-fix items, and the five
review №3 merge-gate items — each with its **terminal** verdict
(MET / PARTIALLY MET / NOT MET) and a one-line evidence pointer to the
reproduction (yours) that grounds it. Where a terminal verdict differs from
the implementer's claimed closure, flag the row.

## Mandate E — exhaustive whole-project validation

The delta mandates above do not exhaust the mission. Before the verdict,
sweep the whole system once more, prioritizing what no prior review probed:

- **Wire parity**: the 35-probe harness plus your own exploratory probing —
  undeclared paths, methods on declared paths, header-variant behavior
  (missing/garbage `X-Okapi-*`), content-type edge cases, and the
  absorption-contract edges from Mandate B.1.
- **Security**: attestation JWTs (alg confusion, `kid` handling, tenant
  claim integrity, expiry); injection through filter values, sort
  expressions, match properties into JPA Criteria — including through the
  new absorption pre-pass, which deliberately preserves raw spellings;
  secrets or SQL text in error bodies and logs; the signing-key cache under
  cross-tenant access; the `/admin` exposure posture (Mandate B.6).
- **Data continuity**: the full runbook walked cold on a rig you populated —
  backup, canary wave via `rollout.sh`, rollback (Phase 6) executed and
  routing-verified, re-cutover after rollback. The R-CERT rehearsal is the
  implementer's walk; this is yours.
- **Scale/performance**: pool-size bursts on hot paths; listing endpoints
  under pathological-but-legal filters (including maximal absorption
  payloads); multi-tenant enable storms; cache-eviction pressure with the
  metrics endpoint under scrape.
- **Spec governance**: re-run `sdd validate --semantic --branch main-final`
  and diff your counts against the claim (165 / 0 / PASS 245/0/0/0);
  spot-check that TRC code symbols exist at HEAD and that the M8 spec
  rewrites (REQ-020 AC6, REQ-022 AC1/AC6, ADR-012, TRC-023) match what the
  code actually does.
- **Release path**: clean-checkout build → image → boot → harness, per the
  checked-in workflows' logic; SBOM and descriptor validation; descriptor
  determinism (the implementer claims byte-identical regeneration —
  reproduce it).
- Anything you probe and find sound belongs in the coverage map as
  *verified*; anything you skip belongs there as *unprobed*. Silence is not
  coverage.

## Deliverables

One review report, stored at `docs/migration/bmad-party-review-4-report.md`
(your only repository write), containing:

1. **Closure matrix** — every review №3 finding and residue item, each
   CLOSED / PARTIALLY CLOSED / OPEN / REGRESSED with the reproduction you
   ran.
2. **Merge-gate table** — the five items, MET / PARTIALLY MET / NOT MET
   with your evidence.
3. **Terminal-arc table** — Mandate D's consolidated 9+7+5 view.
4. **Findings register** — new findings only (numbering continues at
   **F-37**): `ID | Severity | What you observed | Reproduction (command +
   output excerpt) | Recommended action`. A behavioral deviation absent
   from D-1..D-32 is at minimum Major. A committed evidence artifact you
   could not independently reproduce is at minimum Major. An unstated or
   understated residual is a finding.
5. **Coverage map** — verified vs unprobed, explicitly.
6. **Dissent section** — unresolved disagreements, strongest form of each.
7. **Final recommendation** — go / conditional-go (exact conditions) /
   no-go for production cutover, plus the must-fix-before-PR-merge list
   regardless of cutover timing.

## Acceptance bar for THIS review

The review itself fails if any of these are true: a finding was marked
CLOSED without re-running its original reproduction; no reviewer stood up a
real Okapi and drove `rollout.sh` through a control wave **and a forced
failure of a class the implementer did not rehearse live**; the unmodified
35-probe harness was not re-run end-to-end on both sides; the committed
evidence hashes were not independently recomputed; the tenant-surface gate
and the API drift gate were never watched to fail a mutated build; no
semantic verdicts were re-judged live against the cached corpus; no reviewer
hunted D-33 around the absorption contract; the release image was not
rebuilt from HEAD before image-based verdicts; the residuals list was taken
at its word instead of audited; a previous report of yours was treated as
settled truth instead of re-derived; or dissent was smoothed over instead of
recorded. An honest verdict here may well be **GO** — but only if it
survives everything above.
