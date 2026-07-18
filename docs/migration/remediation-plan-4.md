# Remediation plan №4 (M9) — response to BMAD Party review №4

**Produced:** 2026-07-23 via the BMAD correct-course workflow (autonomous batch mode)
**Trigger:** `docs/migration/bmad-party-review-4-report.md` — fourth **NO-GO** against
`feat/migration-01` (application range `950764a..63c8b2f`, review checkout `0977513`)
**Approval:** user directive 2026-07-23 — "continue with remediation-plan-4.md"
(plan authorized); execution approved 2026-07-23 with DP-1(a)/DP-2(a)/DP-3(a)
confirmed (see §3)

---

## 1. Issue summary

Review №4 is the first review that certifies the migration core. The independent
team rebuilt HEAD (3 unit + 95 IT green), booted the Java 21 image, ran the
unmodified 35-probe harness both sides, recomputed all retained evidence
(189/189 bodies, 60/60 rollout files, every hash), live-re-judged five cached
semantic verdicts exactly, and drove the rewritten `rollout.sh` through real
Okapi with **three failure classes the implementer never rehearsed** (port loss,
rollback sabotage, response loss). Verdicts: the rollout complex **MET**
outright; F-27/28/29/30/31/33/35/36 and the F-22/F-24 residues **CLOSED**;
merge gates 1 and 4 **MET**.

The NO-GO rests on **9 fresh Major findings (F-37..F-45)**, no Blockers,
concentrated in four areas — every code pin re-verified by the executing agent
against the working tree before this plan was drafted; all hold:

1. **Two wire-parity regressions in the listing grammar (F-38, F-39).** Escaped
   logical tokens (`\&&`, `\||`) before an empty RHS bypass the R22 absorption
   pre-pass (`KiwtFilterParser.java:71,91` — absorption fires only on a
   *structural* AND/OR token), so the port drops the filter and returns the
   **unfiltered collection** where legacy returns one literal-matched row; and
   `%` wildcard semantics diverge in both `=~` and `match` paths, falsifying
   REQ-022 AC1/AC5's "literal, as legacy" claim. F-21 is REGRESSED a second
   consecutive time; merge gate 2 stays PARTIAL until the oracle work is
   matrix-driven, not case-driven.
2. **Destructive-intent gap (F-37).** `TenantPurgeFlagAdvice.java:66` checks
   `hasNonNull("purge")` — presence only. Jackson then coerces `"true"`/`1` to
   Boolean and takes duplicate keys last-one-wins: four schema-invalid shapes
   were shown to **drop a tenant schema**. Merge gate 3 stays PARTIAL.
3. **Tooling/honesty defects (F-40, F-41, F-45).** The reusable evidence proxy
   persists `Proxy-Authorization` and credential-shaped query parameters
   verbatim (committed evidence is clean — tool defect, not corruption);
   TRC-023 still claims "per-table row counts identical to the r13b ground
   truth" while the IT samples 6 of 37 tables; the M8 residual annex omitted
   the direct-port trust residual, the missing disable-cache eviction test, and
   the one-off (non-recurring) nature of the rollout failure evidence.
4. **Deployment/security surface (F-42, F-43, F-44).** The README-linked k8s
   template routes 8080 while the image listens on 8081 (deterministic dead
   Service); `/admin/loggers` accepts unauthenticated POST on the module port;
   and the attestation fallback (`AttestationController.java:46`) parses
   `X-Okapi-Token` without signature verification — legacy parity, but the
   trust boundary it presumes is unenforced anywhere in the repository.

**The exit path is explicitly narrower now.** The report waives a fifth
wholesale re-review: after remediation, independent reproduction of **nine
named gates** suffices, *provided no unrelated application behavior changes
ride along*. M9 therefore carries a standing minimal-diff constraint: every
`src/main` change must trace to a finding.

## 2. Impact analysis (checklist digest)

- **Program impact.** M0–M8 stand — review №4 *confirms* M8's substance and
  closes 10 items. New remediation milestone **M9** (Direct Adjustment),
  followed by the reviewer-specified targeted verification round (R-VER), not a
  wholesale review №5.
- **Spec impact (governed SDD sessions required).** REQ-022 (AC1/AC5 are
  *falsified* by F-39 and under-specified for F-38 — amendments derived from a
  fresh legacy oracle; candidate deviations **D-33**/**D-34**); TRC-023
  (scope-honest rewrite once the IT is extended); one new **ADR** for the
  attestation/management trust boundary (DP-2/DP-3 outcome); REQ-020 AC6
  behavior scenarios extended with the adversarial purge shapes.
- **Code impact.** `TenantPurgeFlagAdvice` (strict Boolean + duplicate
  detection — F-37); `KiwtFilterParser`/`KiwtListing` (raw-position-aware
  absorption, wildcard semantics — F-38/F-39); `application.yml` management
  exposure (F-43); attestation fallback **only if** DP-3 selects hardening.
- **Ops/docs/test impact.** `proxy.py` redaction channels (F-40);
  `scripts/k8s_deployment_template.yaml` + rendered smoke (F-42, NetworkPolicy
  carrier for DP-3); `AdoptedSchemaUpgradeIT` 37-table ground-truth extension
  (F-41); new disable-cache eviction IT + recurring rollout lane + corrected
  residual annex (F-45); harness `probes.tsv` grows new escaped/wildcard/purge
  rows (documented evolution, as in R22).
- **Not affected.** Legacy `service/` (never modified); the six generated API
  surfaces; data-adoption path; number-generator algorithms; the rollout.sh
  complex (certified MET — only its *recurrence*, not its behavior, changes).

**Path forward evaluated:** Direct Adjustment (add M9 workstreams) — effort
Medium, risk Low/Medium — **selected**. Rollback rejected (nothing shipped in
M8 is invalidated; F-38 is an incompleteness of R22's fix, not a wrong model).
MVP re-scope rejected (the reviewer's own closing paragraph defines a
reachable, bounded exit).

## 3. Decision points (user-gated)

Three findings admit more than one defensible remedy — the review's dissent
section itself records both sides. Each DP below carries a **recommended
default**; the affected workstream phase does not land until the user confirms
or overrides at plan approval. All other workstreams are fully specified and
need no decision.

### DP-1 — F-39 `%`/`_` wildcard: parity vs registered deviation (gates R32 Phase 2)

- **(a) RECOMMENDED — bug-for-bug parity.** Replicate the oracle-derived legacy
  semantics exactly (including any wildcard passthrough into ILIKE), amend
  REQ-022 to describe reality. Rationale: wire compatibility is the program's
  prime directive; the listing surface is read-only, so wildcard passthrough is
  a pattern-match quirk consumers may depend on, not an injection surface.
- **(b) Literal semantics + deviation D-34.** Keep the port's safer
  literal-matching, register D-34 with the full behavioral delta, amend REQ-022
  accordingly. Choose this only if the oracle shows legacy behavior is
  internally inconsistent to the point of being un-replicable.

### DP-2 — F-43 management surface posture (gates R36)

- **(a) RECOMMENDED — narrow the exposure.** `management.endpoints.web.exposure.include:
  health, metrics` (drop `loggers`); document per-deployment re-enablement.
  Rationale: nothing in the runbook or evidence ever used the loggers endpoint;
  F-36's metrics obligation is untouched; smallest diff, eliminates the finding.
- **(b) Authenticated/isolated management listener.** Separate management port +
  enforced access control. Materially larger surface change late in the
  program; choose only if operations require runtime log-level mutation.

### DP-3 — F-44 attestation trust boundary: accept-and-enforce vs harden (gates R37, part of R35)

- **(a) RECOMMENDED — formal acceptance with enforcement artifacts.** Keep the
  legacy-parity fallback (unverified-token `user_id` extraction when
  `X-Okapi-User-Id` is absent), and make the presumed boundary real: a new ADR
  pinning the Okapi-only module-port trust model and identity-source order; a
  NetworkPolicy restricting module-port ingress to Okapi in the (fixed) k8s
  template; a README security section. Rationale: the module has no trusted
  key material to verify Okapi dev/legacy tokens against — verification here
  would break legacy parity and working deployments; the reviewer's own
  dissent (№7) frames this as "resolve **or formally accept**".
- **(b) Harden the fallback.** Verify signature/algorithm/issuer/tenant/expiry
  against a trusted key; reject unverifiable tokens. A behavior change beyond
  legacy with real breakage risk for existing token shapes; requires a key-
  distribution design that does not exist in the legacy FOLIO model.

**Confirmed 2026-07-23 (user):** DP-1(a) bug-for-bug parity, DP-2(a) narrow
the exposure, DP-3(a) formal acceptance with enforcement — all three
recommended defaults accepted together with plan approval. No workstream
remains decision-blocked.

## 4. Workstreams

Numbering continues from M8 (R22–R29). Each workstream ends in its own commit.
Standing rules: legacy `service/` untouched; every spec change through a formal
governed SDD session with `sdd validate --semantic --branch main-final`; one
Maven build at a time; rig usage serialized; context7 documentation check
before touching Spring/actuator configuration; minimal-diff discipline (§1) —
no behavior change without a finding behind it.

### R30 — Strict explicit-purge discriminator (F-37; merge gate 3)

`TenantPurgeFlagAdvice` already buffers the raw body — extend `carriesPurge`
into a strict wire-truth gate implemented as a **token-stream walk** of the raw
JSON (not `readTree`, which silently collapses duplicate keys):

- exactly **one** top-level `purge` member, of JSON **Boolean** type → explicit;
- non-Boolean scalar (`"true"`, `"false"`, `1`, `0`), duplicate `purge` members
  (either order), or any other malformed shape → 400 with a `purge.not.explicit`-
  family error and **provably zero side effects**;
- omitted / `null` / nested-only `purge` → existing R25 behavior (unchanged);
- malformed JSON continues to the converter's `malformed.json` 400 path (D-22).

Governed SDD session: REQ-020 AC6 behavior scenarios extended with the review's
adversarial matrix; DTO/ADR-012 wording checked (contract already says explicit
Boolean — likely scenario-only). ITs: all eight review shapes
(`"true"`, `"false"`, `1`, `0`, both duplicate orders, nested control,
malformed control) with schema-state assertions each side.

**Acceptance:** review's F-37 reproduction rows all 400/side-effect-free;
existing R25 lifecycle ITs stay green. **Effort:** M. **Independent.**

### R31 — Escaped-token absorption completeness (F-38; merge gate 2; D-33 candidate)

Two phases, empirical first — the R22 method, this time matrix-driven:

**Phase 1 — legacy oracle (rig: legacy jar :8080 + testing_pg).** Escaped-
operator matrix against booted legacy, captured as
`docs/migration/evidence/r31-escaped-oracle/`: `\&&` / `\||` before empty RHS
(the review's two rows re-derived), escaped operator mid-expression with
non-empty RHS, multiple escapes, escaped tokens inside quoted values, escaped
`!`/`(`/`)`, lone `&` / lone `|`, `\&&` combined with a real `&&`, and the
unescaped controls. Working hypothesis to confirm/refute: legacy splits on the
first comparison operator and treats the entire raw remainder as literal value
whenever no *unescaped structural* token follows — i.e. absorption generalizes
to raw-text semantics, not token-list semantics.

**Phase 2 — decide, specify, implement.** Make the absorption pre-pass
**raw-position-aware**: the port must never respond to an expression legacy
accepts as a literal comparison by dropping the filter (the one-row →
unfiltered expansion is the defect class). Governed SDD session extends REQ-022
with escaped-boundary acceptance criteria; **D-33 registers only if** a
deliberate port divergence survives the oracle. `KiwtListingGrammarIT` gains
every matrix row; harness `probes.tsv` gains the discriminating rows (and the
allowlist, only if D-33 exists).

**Acceptance:** all matrix rows identical (or governed-deviation) against
booted legacy AND port; the review's two F-38 reproductions return legacy's
one-row result; `mvn -B verify` + semantic lane green. **Effort:** M-H.
**Depends on:** rig; shares its oracle session with R32 Phase 1.

### R32 — `%`/`_` wildcard semantics (F-39; merge gate 2; D-34 candidate; REQ-022 falsity)

**Phase 1 — legacy oracle (same rig session as R31).** Fixture rows `ab_cd`,
`abXcd`, `ab%cd` (review's discriminators) plus `\%`-escaped forms; matrix over
`=~`, `!~`, `match` (single- and multi-property, multi-term), quoted values,
and `==`/`!=` controls. Captured as `docs/migration/evidence/r32-wildcard-oracle/`.
The review's three observations (legacy `=~ab%cd` → `[]`; legacy match →
3 rows; both `==` → 1 row) are re-derived, not trusted.

**Phase 2 — DP-1 gated.** Under the recommended default (parity): implement the
oracle-derived legacy semantics in the contains-filter and text-match paths;
governed SDD session **corrects REQ-022 AC1/AC5** (currently false) to state
the actual semantics; no deviation entry needed. Under DP-1(b): literal
semantics stay, D-34 registers the full delta, REQ-022 amended accordingly.
Either way: `KiwtListingGrammarIT` matrix rows + harness rows both sides.

**Acceptance:** matrix green against both sides under the chosen governance;
REQ-022 no longer contains a claim the wire disproves. **Effort:** M-H.
**Depends on:** rig, DP-1 (Phase 2 only).

### R33 — Proxy credential channels (F-40; merge gate 5)

`evidence/r20-rehearsal/proxy.py` (the reusable hardened tap):

- add `Proxy-Authorization` (and any `*-authorization` header) to the
  redaction set — persisted as `[REDACTED sha256:…]`, still forwarded intact;
- redact **credential-shaped query parameters** before any persistence (path
  fields, JSONL records, capture filenames): `access_token`, `token`, `jwt`,
  `api_key`/`apikey`, `password`, `secret`, `authorization`, case-insensitive —
  value replaced by a correlation hash;
- regression self-test (scripted probe set: multi-value, mixed-case, query +
  header combinations) committed beside the proxy so R-VER and future
  rehearsals can prove the property mechanically.

**Acceptance:** the review's F-40 reproduction persists zero cleartext
credentials; existing r20/rcert evidence untouched (already clean).
**Effort:** L-M. **Independent.**

### R34 — Data-continuity honesty, both directions (F-41; merge gate 5)

Two-sided remedy — strengthen the assertion *and* scope the claim:

- `AdoptedSchemaUpgradeIT`: extend the ground-truth comparison from the
  6-entry `EXPECTED_LEGACY_ROWS` sample to **all 37 tables** of the committed
  r13b `rowcounts.tsv` fixture (the fixture already exists; the IT gains a
  parse-and-compare loop), keeping the existing catalog-name census, no-shrink
  all-table check, and wire readbacks.
- Governed SDD session: TRC-023 rewritten to state **exactly** what is
  asserted — table-name census, 37-table row counts vs ground truth, generator
  + widget wire readbacks, targeted DDL properties — with bit-for-bit row
  *content* identity named as the honest residual (per-column value comparison
  stays out of scope).

**Acceptance:** extended IT green; semantic lane PASS on the modified TRC;
no traceability claim exceeds an executed assertion. **Effort:** M.
**Independent.**

### R35 — Deployment surface: routable template + boundary carrier (F-42; DP-3 carrier)

- `scripts/k8s_deployment_template.yaml`: `containerPort: 8081`, Service
  `port: 8081` with explicit `targetPort: 8081`; liveness/readiness pointed at
  `/admin/health` on 8081 if absent.
- **If DP-3(a) confirmed:** add the NetworkPolicy restricting module-port
  ingress to Okapi (label-selector template with a documented placeholder), as
  the enforcement artifact R37's ADR cites.
- Rendered-manifest smoke: a test (JUnit resource test parsing the template)
  asserting the template's container/service/target ports all equal the
  application's configured `server.port` — so the drift class F-42 exposed
  fails the suite, not a reviewer.
- README deployment section aligned.

**Acceptance:** smoke test fails on the pre-fix template (drift probe), passes
post-fix; `kubectl apply --dry-run=client`-equivalent validation clean.
**Effort:** L-M. **Depends on:** DP-3 for the NetworkPolicy block only.

### R36 — Management surface hardening (F-43; DP-2 gated)

Under the recommended default DP-2(a), after a context7 check of current
Spring Boot actuator guidance: `management.endpoints.web.exposure.include:
health, metrics` — `loggers` removed; a focused IT asserting `GET/POST
/admin/loggers/**` → 404 while `/admin/health` and `/admin/metrics/**` stay
200 (F-36's obligation intact); README documents per-deployment re-enablement
and its risk. Under DP-2(b): separate authenticated management listener
(larger change, specified only if selected).

**Acceptance:** the review's F-43 reproduction (unauthenticated TRACE flip) is
impossible against the rebuilt image; metrics IT from R29 still green.
**Effort:** L. **Depends on:** DP-2.

### R37 — Attestation trust boundary (F-44; DP-3 gated)

Under the recommended default DP-3(a) — formal acceptance with enforcement:

- Governed SDD session: new **ADR** pinning the attestation identity model —
  `X-Okapi-User-Id` (Okapi-supplied, trusted) preferred; token-claim fallback
  documented as legacy parity valid **only** behind an Okapi-exclusive module
  port; the enforced boundary (R35's NetworkPolicy + deployment guidance)
  named as the control; explicit statement that direct module-port exposure
  voids the assertion trust.
- README security section; completion-report residual updated from "unprobed"
  to "accepted and enforced boundary".
- **No `src/main` change** on this path (minimal-diff constraint).

Under DP-3(b): fallback verification design (key distribution, algorithm
allow-list, tenant binding) — specified only if selected; flagged as a
legacy-parity break requiring its own oracle round.

**Acceptance (a-path):** ADR committed + semantic lane PASS; reviewer gate
"management-security probe" satisfiable by document + NetworkPolicy + R36.
**Effort:** L (a) / M-H (b). **Depends on:** DP-3, R35.

### R38 — Residual honesty + recurring safety lanes (F-45)

- New `TenantDisableCacheEvictionIT`: warm **both** tenant-scoped caches
  (widget-definition tenant cache, attestation keypair cache) with real
  traffic, disable the tenant (`purge:false`), prove both evictions
  behaviorally (next call misses / re-loads), not just via DB side effects.
- Recurring rollout lane: a self-contained scripted rehearsal driver
  (`docs/migration/harness/rollout-lane.sh` + compose service defs) that
  stands up Okapi + both modules, runs a control wave and one forced failure
  + rollback + resume end-to-end, and exits non-zero on any gate miss —
  runnable on demand and documented in the runbook as a **named pre-release
  gate with an owner** (the reviewer's minimum: recorded gaps + concrete
  automation owners).
- Completion-report M9 annex: residual list corrected — adds the direct-port
  trust boundary (now governed via R37), the previously-omitted disable-cache
  gap (closed by this workstream), the one-off-evidence nature of prior
  rollout runs (mitigated by the lane), and carries the still-true residuals
  (bit-for-bit row content, production-scale soak, multi-replica key creation,
  24-hour timer fire, hosted SBOM publication) verbatim.

**Acceptance:** eviction IT green; lane run green end-to-end once; annex
enumerates every reviewer-named omission. **Effort:** M. **Independent**
(lane exercises the certified rollout.sh unchanged).

### R-VER — M9 targeted verification round (the reviewer's nine gates)

Not a wholesale re-certification — exactly the report's closing list, each
reproduced at the closing HEAD and captured under
`docs/migration/evidence/rver/`:

1. targeted purge matrix (R30's adversarial shapes over the wire);
2. escaped/`%` legacy oracle re-run (R31/R32 matrices, both sides);
3. full-probe harness both sides + adoption leg, `diff-runs.sh` exit 0;
4. proxy credential suite (R33 self-test);
5. data-continuity pins (extended `AdoptedSchemaUpgradeIT` + `FreshDdlCatalogIT`);
6. rendered k8s service smoke (R35 test + dry-run validation);
7. management-security probe (loggers 404, health/metrics 200, boundary docs);
8. semantic live sample (`sdd validate --semantic` full gate; expect all-PASS
   with fresh verdicts on every M9-touched spec);
9. one real-Okapi failure/rollback/resume wave with the corrected artifacts.

Plus: full `mvn -B verify`, rebuilt image, evidence forensics recompute,
dossier + completion-report M9 annex, journal closure. Review №5 convening /
push / PR remain user-gated.

**Effort:** M-H (dominated by the Okapi wave). **Depends on:** all of R30–R38.

## 5. Dependencies and execution sequencing

```
R31 ─┬─ shared legacy-oracle session ─→ fixes → SDD → ITs → harness   [rig]
R32 ─┘        (R32 Phase 2 gated on DP-1)
R30, R33, R34                                    [independent, no rig]
R35 ──→ R37   (NetworkPolicy + ADR pair; both gated on DP-3)
R36           (gated on DP-2)
R38           [rig for the lane's one green run]
ALL ──→ R-VER [rig + rebuilt image + Okapi]
```

Round plan (compaction pause between rounds, per standing rule):

| Round | Content | Commits |
|---|---|---|
| 0 | this plan | `docs(m9): remediation plan 4` |
| 1 | R31 + R32 | shared oracle evidence; parser fixes; SDD (REQ-022, D-33/D-34); ITs; harness |
| 2 | R30 + R33 | strict purge discriminator; proxy channels |
| 3 | R34 + R38 (IT + lane) | 37-table continuity + TRC-023; eviction IT; rollout lane |
| 4 | R35 + R36 + R37 | template + smoke; management exposure; trust-boundary ADR |
| 5 | R38 (annex) + R-VER | nine-gate verification evidence + M9 closure |

Serialization: one Maven build at a time; the rig serves Round 1 (oracle),
Round 3 (lane), and R-VER — no concurrent rig mutation; SDD sessions strictly
sequential. DP confirmations are needed **before Round 1 completes Phase 2 of
R32** and before Round 4 starts; Rounds 1–3 are otherwise unblocked.

## 6. Handoff and merge-gate mapping

**Scope classification:** Moderate — executed directly by the developer agent
under the standing full-autonomy rule once the user confirms the DP defaults;
no backlog reorganization beyond adding M9.

Review №4 must-fix item → workstream:

| Must-fix | Workstream(s) |
|---|---|
| 1. F-37 strict Boolean purge, all invalid shapes side-effect-free | R30 |
| 2. F-38/F-39 corrected or governed from a booted-legacy oracle; REQ-022 + register + IT + harness aligned | R31, R32 |
| 3. F-40 every proxy credential channel redacted | R33 |
| 4. F-41 TRC-023 narrowed or continuity assertions broadened | R34 (both) |
| 5. F-42 k8s template on 8081 + service-level smoke | R35 |
| 6. F-43 loggers removed/secured; F-44 boundary enforced or formally accepted | R36, R37 (+R35) |
| 7. F-45 recurring gaps recorded with automation owners | R38 |
| (post-fix reproduction set: the nine named gates) | R-VER |

Success criterion for M9: all seven must-fix items closed with reproducible
evidence, the nine reviewer-named gates green at the closing HEAD, full suite +
semantic lane green, minimal-diff constraint held (every `src/main` change
traceable to F-37/F-38/F-39 or a confirmed DP), and the program ready for the
user-gated targeted re-verification / PR decision — per the reviewer, **no
fifth wholesale review required**.
