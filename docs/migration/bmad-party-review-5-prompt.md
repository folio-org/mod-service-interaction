# Review mission brief №5 — exhaustive final validation after the M9 remediation

> **How to run**: convene BMAD Party Mode with this file as the opening intent
> (`/bmad-party-mode --mode subagent` or `--mode agent-team` so every persona
> thinks independently; add `--non-interactive` for an unattended run to a
> natural close). Suggested room: Analyst, Architect, Dev, QA/Test Architect,
> PM, Tech Writer — open-cast a Security reviewer (the attestation trust
> boundary is a first-class dimension this time) and an SRE when their
> dimensions come up. Reuse persona ownership from reviews №1–№4 where it
> helps continuity, but every verdict must be re-derived, not remembered.

---

## Mission

You are the **independent review team** that has returned **NO-GO four
times** on the `mod-service-interaction` Grails→Spring Boot migration: at
`d5ba8ea` (№1), `0d7dd33` (№2), `950764a` (№3), and after M8 at the R-CERT
state (№4, `docs/migration/bmad-party-review-4-report.md` — rollout complex
MET, 10 of 16 review-№3 items CLOSED, but 9 new Majors **F-37..F-45** with
F-21 REGRESSED a second time through the parser, and merge gates 2/3/5
PARTIAL).

Review №4 ended with an offer: a fifth **wholesale** review was waived
conditional on **nine named targeted gates**, provided no unrelated behavior
changes rode along (the minimal-diff constraint). The module owner has
chosen NOT to take the waiver — this review is convened as the full,
exhaustive validation anyway. Read that choice correctly: it strengthens
your mandate, never narrows it. You verify the nine gates **and** sweep the
whole system; the waiver text binds the implementer's delta, not your scope.

The implementer has since executed a fourth remediation program (M9, plan at
`docs/migration/remediation-plan-4.md`, workstreams R30–R38 + R-VER) landing
**14 commits on `feat/migration-01`** on top of the №4 state (`000b8a6` plan
… `b14843c` annex), under three decision points the module owner confirmed
explicitly: **DP-1(a)** bug-for-bug `%`/`_` wildcard parity, **DP-2(a)**
`loggers` dropped from the management exposure, **DP-3(a)** the legacy
attestation trust model accepted and *enforced as a deployment boundary*
(ADR-013 + NetworkPolicy + README + template drift test — deliberately no
`src/main` change on that path). The claims: every F-37..F-45 finding
closed; the nine waiver gates reproduced at the closing HEAD
(`docs/migration/evidence/rver/`); `mvn -B clean verify` with 7 unit +
**106 integration tests** and 0 failures; semantic gate 166 files / PASS
**249/0/0/0** with **19 live model verdicts on every M9-touched spec pair**
(cache deliberately evicted first); the harness grown to a 44-probe capture
with the adoption diff again at **0 divergences**; two new registered
deviations (**D-33**, **D-34**); a recurring real-Okapi rollout rehearsal
lane installed as runbook pre-release gate 4.G and re-run green; and an
evidence-forensics pass in which every previously documented hash recomputed
clean.

Your job is to **try to break that claim — a fifth time**. This is the last
gate before the PR and production cutover. The M9 delta is the priority
attack surface, but your verdict covers the *whole* migration, and anything
unprobed must be named as unprobed. You owe the implementer nothing, and you
owe your own four previous reports nothing either: a finding the remediation
proves wrong gets retracted with evidence, exactly as an unfixed finding
gets re-confirmed with evidence. Deliver **go / conditional-go / no-go**,
backed by findings you verified yourselves.

**Cardinal rule — empirical over textual.** The project's history now proves
it five times over, and the fifth instance came from the implementer's own
verification round: R-VER's first full harness populate since the M9 parser
rows landed **failed** (a fixture-ordering assumption nobody had re-run),
and the oracle re-run surfaced a wire divergence (**D-34**: a naive client's
raw unencoded `%` — legacy Tomcat 9 answers 200-empty, port Tomcat 10.1
rejects 400 at the container) that **no MockMvc-based IT can ever observe**.
Before that, review №3's F-26 "legacy drops the empty clause" consensus —
implementer and reviewers alike — was destroyed by a 10-probe matrix against
the booted module. Nobody's untested claim has survived contact with the
booted module — including yours. Any finding you can verify by *running
something* must be verified by running it. A claim you could not reproduce
is UNVERIFIABLE, never confirmed.

**Honesty audit.** The M9 annex (`completion-report.md`
§"M9 remediation outcome") carries a **corrected residual list**: it removes
three items as resolved/mitigated (the attestation trust boundary — now
ADR-013 accepted+enforced; the tenant-disable cache-eviction gap; the
one-off nature of the rollout evidence) and carries the still-true residuals
verbatim (bit-for-bit row content, production-scale soak, multi-replica key
creation, 24-hour timer fire, hosted SBOM, directed-not-fuzzed grammar, one
live-rehearsed failure class). Review №4's F-45 was precisely an *omitted*
residual — audit this list for completeness and accuracy as its own line of
attack: an unstated residual is a finding, and so is a stated one whose
framing understates its risk, and so is a "resolved" row whose resolution
does not actually hold.

**Minimal-diff audit — new in this review.** The №4 waiver bound the
implementer to a delta in which **every `src/main` change traces to a
finding or a confirmed decision point**. Diff `src/main` across the entire
M9 range yourself and demand a finding-or-DP pedigree for every hunk. An
untraceable behavior change is a finding regardless of whether it looks
benign — it voids the constraint the remediation was executed under.

**Independence rules** (unchanged)

- Implementer documents — `completion-report.md` (including the M9 annex),
  all four remediation plans, the deviation dossier, the runbook,
  `.sdd/handoffs/`, the `evidence/rver/` READMEs — are *claims under
  review*, not evidence.
- Reproduce before judging; attach command + output to every verdict.
- Preserve disagreement; the report carries both positions.
- The repository is **read-only** for you, with one exception: your report
  is stored as `docs/migration/bmad-party-review-5-report.md` and is the
  **only repository write** you make. Scratch work, captures, diffs, and all
  mutation experiments happen on throwaway copies outside the repo.

## Inputs

| Artifact | Role |
|---|---|
| `docs/migration/bmad-party-review-4-report.md` | Your review №4 — findings F-37..F-45, the nine waiver gates, the minimal-diff constraint |
| `docs/migration/remediation-plan-4.md` | The M9 program (claim), incl. §3 decision points DP-1/2/3 with the owner's confirmations |
| `git log` for the 14 M9 commits (`000b8a6..b14843c`) | The remediation delta — read it commit-by-commit; `d0ae4f7`/`51ef9a5`/`b14843c` are the R-VER closing commits |
| `service/` | The legacy module — **ground truth** for all behavior; never modified, verify that too |
| `src/`, `pom.xml`, `descriptors/`, `Dockerfile`, `scripts/k8s_deployment_template.yaml`, `.github/workflows/`, `Jenkinsfile` | The port and its release path under review |
| `specs/` | Governed spec universe — 166 files; REQ-001..022 (REQ-020 desc+AC6 and REQ-022 AC1/AC5 rewritten, AC7 **new** in M9), ADR-001..**013** (ADR-013 new: attestation trust boundary), TRC-001..024 (TRC-023 rewritten) |
| `docs/migration/wire-compat-deviations.md` | Deviation register, now **D-1..D-34** (claim of completeness; D-33 multi-term, D-34 raw-`%` are M9-registered) |
| `docs/migration/harness/` | `probes.tsv` (**45** rows — the 44 captured in R-VER plus the D-34 row added after that capture, an explicitly documented evolution), `populate.sh` (R-VER-fixed seeded-generator selection), `capture.sh`, `diff-runs.sh`, `deviation-allowlist.tsv` (D-1..D-34), `rollout.sh` (unchanged since R23 — verify that), **`rollout-lane.sh` + compose + preprobes (new: the recurring rehearsal lane, runbook gate 4.G)** |
| `docs/migration/cutover-runbook.md` | Now ends Phase 4 with **pre-release gate 4.G** (the lane, with a named owner) |
| `docs/migration/evidence/r31-escaped-oracle/`, `/r32-wildcard-oracle/` | The M9 legacy oracles for F-38/F-39 — matrix drivers under `harness/`, per-probe bodies, parse trees, SQL binds |
| `docs/migration/evidence/rver/` | The R-VER bundle: nine-gate evidence (`purge-matrix/`, `oracle-rerun/`, `harness/`, `okapi-wave/`, per-gate txt files), `build.txt`, `forensics.md` — the implementer's execution of YOUR waiver gates |
| `docs/migration/evidence/semantic/` + `semantic/rcert/` + `semantic/rver/` | Verdict corpora: R27 baseline (245, all-cache), R-CERT recapture (245, all-cache), **R-VER recapture (249 — 230 cache + 19 live model verdicts on the M9-touched pairs)** |
| Prior bundles (`r13-legacy/`, `r18/`, `r20-rehearsal/`, `r22-legacy-compound/`, `r23-dryrun-probes/`, `r26-drift-probe/`, `rcert/`) | Historical evidence — forensics targets; `r20-rehearsal/proxy.py` is now the R33-hardened tap with `proxy_selftest.py` beside it |
| `README.md` | Ops surface under review: R35 deployment/ports paragraph, R36 management-surface paragraph, R37 "Security: the attestation trust boundary (ADR-013)" section |
| `tools/testing/docker-compose*` | Postgres for the rehearsal rig |

Environment facts: port builds with Java 21 (`mvn -B clean verify`; suite
claims **106 integration + 7 unit**); the release image builds from the root
`Dockerfile`, serves **8081**, health at `/admin/health`; legacy jar
`service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar` (sha256
`abb37211…c57`) runs on JDK 17. A rehearsal rig may still be running on this
machine (legacy on :8080 with Hibernate trace flags, an `rver-port`
container on :8081, `testing_pg` Postgres on :54321 with tenants incl.
`r31o`/`rvl`(adopted)/`rvp`/`rvo`, local images
`mod-service-interaction:{r18,rcert,rver}`): treat it as a **convenience,
never as evidence** — any verdict rides on state you built yourself, and you
**rebuild the image from HEAD before any image-based verdict**. Operational
notes that will save you time: Okapi dev mode parses any supplied
`X-Okapi-Token` and 400-rejects non-JWT shapes (use a JWT-shaped dummy or
omit the header); the host has no `psql` client (docker-exec shim into
`testing_pg`); `kubectl --dry-run=client` fails here (stale kubeconfig —
client dry-run still needs API discovery; the implementer's offline
equivalent is `envsubst | kubeconform -strict`, image pulled locally); the
semantic lane needs the local LLM proxy (`sdd.config.yaml#/semantic/model`:
base_url `http://127.0.0.1:54001/v1`, `OPENAI_API_KEY` in env) **and `sdd`
must run from the repo root** — from any other cwd the config is silently
missed and the lane goes inert with an "UNCALIBRATED" gate (the implementer
hit exactly this; an inert run is not a PASS). If the proxy is down, get it
restarted rather than reporting UNVERIFIABLE.

## Mandate A — closure matrix: every review №4 finding re-tested

For **each** of F-37..F-45 — verdict CLOSED / PARTIALLY CLOSED / OPEN /
REGRESSED under the standing definitions: CLOSED requires re-running the
finding's *original reproduction* against HEAD **and** confirming the
claimed pin exists, runs, and asserts the failure mode. Verify against the
closure table in `completion-report.md` §"M9 remediation outcome" — its
commit hashes and IT names are leads, not proof. Spot-checks that MUST
happen:

- **F-37 (purge discriminator)**: over the wire against the rebuilt image —
  your №4 coercion shapes (`"true"`, `1`, duplicates both orders, `[true]`,
  `module_to` variant) must each 400 `purge.not.explicit` **before binding**
  with provably zero side effects; the nested-`purge` control and malformed
  JSON must keep their own paths; explicit Booleans must still work both
  ways. Then attack the mechanism itself (Mandate B.2).
- **F-38 (escaped-token absorption; the F-21 double-regression)**: re-derive
  the escaped-token oracle **yourself** against booted legacy — the
  committed matrix drivers (`evidence/r31-escaped-oracle/harness/`) document
  the directed-fixture technique; reproduce it, don't trust it. Then the
  port: REQ-022 AC7 vs `KiwtFilterParser`'s raw-escape tokenizer vs actual
  wire behavior, `KiwtListingGrammarIT` orders 16–17, and the `r31-*`
  harness rows on BOTH sides. The oracle's strangest results (escape
  prevents *pairing* but the value stays raw; op-spellings absorb; single
  `=`/`<`/`>` re-shape into property errors) are exactly where a
  transcription error would hide.
- **F-39 (wildcards, DP-1(a))**: re-derive the `%`/`_` oracle including the
  **planted-row positive proof** of legacy's broken
  `([^\\])% → $1 + literal "$2"` transform (a row literally named `ab$2cd`
  must match `code=~ab%cd` on BOTH sides — bind `%ab$2cd%`). Verify
  `=i=` is UNWRAPPED ilike, match/term passes wildcards live, and judge
  whether REQ-022 AC1/AC5 describe the shipped bug-for-bug behavior
  precisely. DP-1(a) was the owner's call — your job is to verify the
  *fidelity* of the parity, not to relitigate the choice; but say explicitly
  whether the parity is complete or you found an unregistered wildcard edge.
- **F-40 (proxy credential channels)**: code-review the R33 redaction set
  (`*-authorization` wildcard, query-string credentials, capture-failure
  markers), run `proxy_selftest.py` yourself (claims 33/33), then probe the
  live tap with your own hostile shapes: mixed-case and repeated params,
  novel credential-bearing header names, response `Set-Cookie`. Sweep ALL
  retained evidence for credential-shaped values yourself.
- **F-41 (37-table continuity)**: verify `AdoptedSchemaUpgradeIT` now
  compares **all 37 tables** against the committed r13 ground-truth
  `rowcounts.tsv` fixture (confirm the fixture's own provenance), read
  TRC-023 at HEAD method-by-method against what the ITs actually assert,
  and confirm the bit-for-bit residual is stated literally.
- **F-42 (deployment surface)**: run `K8sDeploymentTemplateTest`, then
  mutation-test it on a throwaway copy — flip a containerPort, drop the
  NetworkPolicy, change a probe path: each mutation must fail the build.
  Render the template and validate it yourself. Judge whether the shipped
  NetworkPolicy (placeholder selector, documented in-file) is an honest
  DP-3(a) carrier or security theater — that judgment feeds Mandate A's
  F-44 row and Mandate E's security sweep.
- **F-43 (management surface, DP-2(a))**: over the wire against the rebuilt
  image — your №4 reproduction (unauthenticated POST
  `/admin/loggers/<name>` `{"configuredLevel":"TRACE"}`) must 404; health
  and metrics must stay 200 with the F-36 cache meters still scrapeable;
  `/admin` discovery must list exactly health+metrics. Then check the
  documented re-enablement path (env override) actually works and its risk
  framing is honest.
- **F-44 (attestation trust boundary, DP-3(a))**: the closure is an ADR plus
  enforcement artifacts, deliberately not code. Verify the whole chain:
  ADR-013 exists, is schema-valid, says what the annex claims; the
  NetworkPolicy + README obligations + drift test are real and consistent;
  the README's "direct exposure voids assertion trust" statement is
  unambiguous. Then **attack the boundary as review №4 did**: with direct
  module-port access, mint an assertion for an arbitrary user id — confirm
  this still works (it must; DP-3(a) accepts it) and that every place a
  consumer could be misled says so. Judge whether accept+enforce is
  *coherently executed*; whether it is *sufficient* is a risk-posture
  verdict you must state explicitly with the deployment assumption named.
- **F-45 (residual honesty)**: run `TenantDisableCacheEvictionIT` and
  verify its warm-cache controls actually discriminate hit-vs-reload; run
  the **rollout lane yourself end-to-end** (`rollout-lane.sh` — ten gates,
  disposable rig, forced failure + rollback + resume against the unmodified
  `rollout.sh`) and confirm runbook gate 4.G names an owner and exit
  semantics; then audit the corrected residual list (Honesty audit above).

## Mandate B — fresh adversarial sweep of the M9 delta

M9 added a raw-escape tokenizer + absorption rework, a legacy-ilike
transform, a token-stream purge discriminator, ~600 lines of new lane bash,
a resource test, an exposure change, a NetworkPolicy, and two governed spec
sessions' worth of rewrites. New code means new defects; your №4 findings
say nothing about it. Hunt for **D-35** — a behavioral difference absent
from D-1..D-34 — and for defects in the new machinery. Numbering for new
findings continues at **F-46**. Priority attack surface:

1. **`KiwtFilterParser` raw-escape tokenizer + boundary-scoped absorption**
   (rewritten in R31 on top of the R22 pre-pass review №4 already broke
   once). Attack with booted legacy as oracle: escape at value boundaries
   (`\` as final char, `\\&&`, `\\\&&`), escapes inside quoted values,
   escaped tokens adjacent to real ones (`a\&&&&b`), op-spelling absorption
   colliding with the 400-identifier re-shape (`code==alpha<beta` vs
   `code==alpha<5e2`), absorption + `sort`/`match`/`stats` interplay, and
   the D-30/D-32 dispositions around every new edge. The two-times-regressed
   F-21 lineage makes this your single most likely fresh find.
2. **`TenantPurgeFlagAdvice.purgeOnTheWire` token-stream walk** (R30
   replaced presence-check with a strict tri-state discriminator). Attack:
   duplicate detection vs Jackson's own duplicate handling; numeric shapes
   (`1e0`, `0.0`); unicode-escaped key (`"purge"` — the wire bytes
   differ but the JSON key is `purge`; which does the walk see, and does it
   match what binding sees?); huge/deeply-nested bodies (walk cost, DoS
   posture); malformed-mid-stream bodies; charset variants; whether
   non-tenant endpoints' bodies are provably untouched by the advice.
3. **`legacyIlikeValue` transform (R32)**: values containing literal `$1`
   or `$2` (the replacement string is itself `$`-bearing — regex
   replacement-escaping bugs live exactly here), multiple `%` in one value,
   `%` at position 0 (legacy leaves leading `%` live — confirm the port's
   exact boundary), `\%` adjacent to `%`, backslash-heavy values, and the
   same matrix through `!~` and `=i=`.
4. **`rollout-lane.sh` (~600 lines new bash)**: it is test scaffolding for
   the certified `rollout.sh` — but a lane that can silently pass is worse
   than none. Verify each of its ten gates can FAIL: sabotage one at a time
   on a throwaway copy (wrong catalog, un-corrupted lanebad, prebuilt-jar
   mismatch, port collisions with the standing rig). Confirm it really uses
   the unmodified `rollout.sh` (hash it), tears down on every exit path,
   and never persists the generated token.
5. **`K8sDeploymentTemplateTest` + the NetworkPolicy**: the resource test
   parses YAML — feed it template mutations it might parse-but-miss
   (a second container, a named port, `targetPort` as string, an extra
   Ingress document routing 8081 publicly — does anything catch that?).
   Judge the placeholder-selector NetworkPolicy operationally: applied
   verbatim it selects `app: okapi` pods — name the failure modes in a
   cluster where that label does not match, and check the README says it.
6. **D-34's own claim**: reproduce the raw-`%` divergence at the container
   level (it is invisible to MockMvc — that is the stated reason it was
   missed; verify that reasoning), check the impact statement ("no
   well-behaved consumer affected") against real FOLIO clients, and probe
   adjacent malformed-encoding shapes (`%`, `%2`, `%zz`, overlong UTF-8) on
   both sides for a D-35 hiding next to D-34.
7. **Evidence forensics on the NEW bundles** (`rver/*`, `semantic/rver/`,
   `r31-escaped-oracle/`, `r32-wildcard-oracle/`): third-party
   reproducibility tested literally — pick rows at random, recompute,
   replay. The rver harness manifests pin a 44-probe list while
   `probes.tsv` now has 45 rows — the bundle documents this as evolution;
   verify the explanation holds. Any hash that does not reproduce is at
   minimum Major.
8. **Semantic fresh-verdict faithfulness.** The R-VER corpus claims 230
   cache + **19 fresh model verdicts** obtained by evicting the M9-touched
   cache entries. Verify the eviction claim is coherent (the 19 fresh
   findings are exactly the M9-touched pairs and none of the untouched
   ones), then force your **own** live re-judge of a random sample —
   including at least 5 of the 19 — with the cache cleared on a throwaway
   copy. A live verdict that flips a PASS is a finding; so is a corpus
   whose fresh/cache split does not reproduce.

## Mandate C — the nine waiver gates, one verdict each

Review №4's closing list — the conditions under which you waived a fifth
wholesale review. The implementer's `evidence/rver/` is *their* execution of
your gates; your verdict requires **yours**. For each, MET / PARTIALLY MET /
NOT MET with what you ran:

1. Targeted purge matrix (R30's adversarial shapes) over the wire.
2. Escaped/`%` legacy oracle re-run (R31/R32 matrices, both sides).
3. Full-probe harness both sides + adoption leg, `diff-runs.sh` exit 0.
4. Proxy credential suite (R33 self-test).
5. Data-continuity pins (extended `AdoptedSchemaUpgradeIT` +
   `FreshDdlCatalogIT`).
6. Rendered k8s service smoke (R35 test + dry-run-equivalent validation).
7. Management-security probe (loggers 404, health/metrics 200, boundary
   docs).
8. Semantic live sample (full gate; fresh verdicts on every M9-touched
   spec).
9. One real-Okapi failure/rollback/resume wave with the corrected
   artifacts.

Plus the standing condition attached to the waiver: the **minimal-diff
audit** (see Mission) — a tenth row in the same table.

## Mandate D — the terminal arc, one consolidated table

This is the fifth and intended-final review. Give the cutover
decision-maker the complete arc in one place: the nine review №1
reconsideration conditions, the seven review №2 must-fix items, the five
review №3 merge-gate items, and the nine review №4 waiver gates (+
minimal-diff) — each with its **terminal** verdict (MET / PARTIALLY MET /
NOT MET) and a one-line evidence pointer to the reproduction (yours) that
grounds it. Where a terminal verdict differs from the implementer's claimed
closure, flag the row.

## Mandate E — exhaustive whole-project validation

The delta mandates above do not exhaust the mission. Before the verdict,
sweep the whole system once more, prioritizing what no prior review probed:

- **Wire parity**: the full harness (both sides + adoption leg, diff exit 0
  — rerun it unmodified) plus your own exploratory probing: undeclared
  paths, methods on declared paths, header-variant behavior, content-type
  and encoding edge cases (the D-34 neighborhood), and the tokenizer/
  transform edges from Mandate B.1/B.3.
- **Security**: the attestation trust boundary end-to-end (Mandate A F-44 —
  and additionally: alg confusion and `kid` handling on the verify side of
  consumers, tenant claim integrity, expiry); injection through filter
  values, sort expressions, match properties into JPA Criteria — the parser
  now deliberately preserves *raw* escaped spellings all the way to the
  bind, so re-test the injection surface post-R31/R32; the `/admin`
  surface posture after DP-2(a) (and what else the module port serves to
  anyone with reach); secrets or SQL text in error bodies and logs;
  cross-tenant isolation of both caches after the eviction changes.
- **Data continuity**: the full runbook walked cold on a rig you populated —
  backup, canary wave via `rollout.sh` (or the lane), rollback executed and
  routing-verified, re-cutover after rollback; the 37-table continuity
  compare re-derived from your own tenant, not just the committed fixture.
- **Scale/performance**: pool-size bursts on hot paths; listing endpoints
  under pathological-but-legal filters (maximal escape/absorption payloads,
  wildcard-heavy values); tenant enable storms; the purge-discriminator
  walk under large bodies; cache-eviction pressure with metrics under
  scrape.
- **Spec governance**: re-run `sdd validate --semantic --branch main-final`
  **from the repo root** and diff your counts against the claim (166 files /
  gate PASS 249/0/0/0 / one advisory warning on ADR-013's missing
  architecture-component ref — judge whether that warning is benign or a
  modeling gap); verify the M9 spec rewrites (REQ-020 AC6, REQ-022
  AC1/AC5/AC7, TRC-023, ADR-013) match what the code actually does; verify
  the governed-session trail exists for every spec change in the M9 diff.
- **Release path**: clean-checkout build → image → boot → harness; SBOM and
  descriptor validation; descriptor determinism (byte-identical
  regeneration is claimed again — reproduce it).
- Anything you probe and find sound belongs in the coverage map as
  *verified*; anything you skip belongs there as *unprobed*. Silence is not
  coverage.

## Deliverables

One review report, stored at `docs/migration/bmad-party-review-5-report.md`
(your only repository write), containing:

1. **Closure matrix** — every review №4 finding F-37..F-45, each CLOSED /
   PARTIALLY CLOSED / OPEN / REGRESSED with the reproduction you ran.
2. **Waiver-gate table** — the nine gates + the minimal-diff audit, MET /
   PARTIALLY MET / NOT MET with your evidence.
3. **Terminal-arc table** — Mandate D's consolidated 9+7+5+10 view.
4. **Findings register** — new findings only (numbering continues at
   **F-46**): `ID | Severity | What you observed | Reproduction (command +
   output excerpt) | Recommended action`. A behavioral deviation absent
   from D-1..D-34 is at minimum Major. A committed evidence artifact you
   could not independently reproduce is at minimum Major. An unstated or
   understated residual is a finding. An untraceable `src/main` change in
   the M9 range is a finding.
5. **Coverage map** — verified vs unprobed, explicitly.
6. **Dissent section** — unresolved disagreements, strongest form of each.
7. **Final recommendation** — go / conditional-go (exact conditions) /
   no-go for production cutover, plus the must-fix-before-PR-merge list
   regardless of cutover timing.

## Acceptance bar for THIS review

The review itself fails if any of these are true: a finding was marked
CLOSED without re-running its original reproduction; no reviewer re-derived
the escaped-token and wildcard oracles against booted legacy (including the
planted-row `$2`-transform positive proof); no reviewer drove the rollout
lane (or `rollout.sh` directly) through a real-Okapi control wave **and** a
forced failure with verified rollback and resume; the harness was not re-run
end-to-end on both sides plus the adoption leg; the purge matrix and the
F-43 log-level flip were not re-probed over the wire against an image
rebuilt from HEAD; the attestation boundary was not attacked from direct
port access and the enforcement chain not walked artifact-by-artifact; the
committed evidence hashes were not independently recomputed; the template
drift test and the tenant/API gates were never watched to fail a mutated
build; no semantic verdicts were re-judged live against the cached corpus
(including a sample of the 19 claimed-fresh ones); no reviewer hunted D-35
around the tokenizer, the ilike transform, the purge walk, or the D-34
neighborhood; the `src/main` diff was not audited hunk-by-hunk against the
minimal-diff constraint; the corrected residual list was taken at its word
instead of audited; a previous report of yours was treated as settled truth
instead of re-derived; or dissent was smoothed over instead of recorded. An
honest verdict here may well be **GO** — but only if it survives everything
above.
