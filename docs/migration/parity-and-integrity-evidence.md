# Data-integrity and functional-parity evidence — legacy `mod-service-interaction` vs the Spring Boot port

**Date:** 2026-07-24 · **Authored by:** the migration implementer
**Scope:** consolidates the evidence that the Java 21 / Spring Boot module at the
repo root is wire-compatible with, and data-lossless relative to, the legacy
Grails 6 module under `service/`. Every claim below carries a pointer to the
artifact, test, or capture that proves it; nothing is asserted on word alone.

## Artifacts under comparison

| Side | Artifact | Identity |
|---|---|---|
| Legacy | `service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar` (Grails 6 / Groovy, JDK 17) | sha256 `abb3721190394d2eea3e5661345b4d0fd8da89add558e0fc9924b26ec79b4c57` |
| Port | `target/mod-service-interaction-5.0.0-SNAPSHOT.jar` (Java 21 / Spring Boot, folio-spring) | sha256 `9611a5bdeb771b06fb70f38a3aea7fcc487007baf4317c2d4ec28962e92f5eeb` (R-VER build); image `mod-service-interaction:rver` Id `sha256:a358168c…` |
| Okapi descriptor | `target/ModuleDescriptor.json`, generated at build | sha256 `6c14c195…` — **byte-identical across three independent rebuilds** (r20, R-CERT, R-VER): the wire contract artifact is deterministic |

State at authoring: branch `feat/migration-01`, spec/src closing HEAD `66c5435`
(M9 R37; later commits add docs/evidence only). `mvn -B clean verify` at that
HEAD: **BUILD SUCCESS, 7 unit + 106 integration tests, 0 failures**
(`evidence/rver/build.txt`). The legacy tree under `service/` was never
modified during the migration (verified per review:
`git diff --quiet 000b8a6^..b14843c -- service` → exit 0, and equivalents in
earlier reviews).

## How this evidence was produced and checked

Three independent layers, so no claim rests on the implementer's own runs:

1. **Continuous, committed pins** — integration tests in `src/test/` and the
   harness under `docs/migration/harness/` that re-prove the claims on every
   build/run, not once.
2. **Captured empirical bundles** — under `docs/migration/evidence/`, each with
   manifests carrying per-body sha256 hashes so the captures themselves are
   auditable (`evidence/rver/forensics.md` recomputes every documented hash).
3. **Five adversarial independent reviews** (BMAD Party Mode,
   `bmad-party-review-*-report.md`) that re-derived the oracles against the
   *booted* legacy module, re-ran the harness with unmodified scripts, rebuilt
   the image from HEAD, and attacked the claims. Review №5 (2026-07-23)
   independently reproduced every parity and integrity result below.

## 1. Data integrity

### 1.1 Adopted-schema upgrade is lossless (the case that matters for real tenants)

**Claim:** upgrading an existing legacy tenant schema to the port changes no
data and executes no DDL against it.

**Pin:** `src/test/java/org/folio/servint/AdoptedSchemaUpgradeIT` (**8/8**),
which restores the production-shaped legacy dump fixture
`docs/migration/evidence/r13-legacy/r13b-populated-schema.sql` (85 KB, captured
from the running legacy module) into a disposable Postgres and proves:

- **all 37 legacy tables' row counts are identical before and after** the
  port's tenant upgrade — parsed from the fixture itself, not hardcoded;
- **all 14 adoption Liquibase changesets finish `MARK_RAN`**, i.e. the port
  recognizes the legacy schema as already-built and never executes DDL on it;
- the only additions are Liquibase's own two bookkeeping tables
  (`tenant_changelog`, `tenant_changelog_lock`);
- a repeat upgrade is a no-op.

**Independent verification:** review №4 forced this test to become honest
(F-41: it originally compared fewer tables); review №5 confirmed the corrected
version "genuinely parses and compares all 37 fixture row counts before/after
upgrade".

### 1.2 Fresh-DDL schema is shape-identical to legacy — including legacy's defects

**Claim:** a tenant created fresh by the port gets the same physical schema a
legacy-created tenant has, so data written by either side is interchangeable.

**Pins:** `FreshDdlCatalogIT` (**4/4**) asserts against
`information_schema` that the port's DDL reproduces legacy exactly, including
the parts a naive rewrite would "fix":

- `refdata_category.internal` keeps **no column default** (legacy quirk);
- `dashboard_access` and `dashboard_display_data` keep their **missing primary
  keys** (legacy defect, preserved deliberately — entities
  `DashboardDisplayData` etc. document this);
- legacy's exact **foreign-key constraint names** exist by name;
- pinned column ordinals match.

`TenantEnableIT` pins the full enable/disable/purge lifecycle including the
schema-per-tenant model (`SCHEMA` multi-tenancy, one Postgres schema per
tenant, same as legacy).

*Known limit (registered, not hidden):* the fresh-schema pins are targeted
spot assertions, **not** a full expected-table census — review №5 finding F-52
tracks the TRC-023 wording that overclaimed this.

### 1.3 Data survives a real production-style cutover — and a failed one

**Claim:** cutover through a real Okapi preserves data, and an aborted cutover
leaves legacy data untouched.

**Pin:** the recurring rollout rehearsal lane
(`docs/migration/harness/rollout-lane.sh` wrapping the unmodified
`rollout.sh`), evidence bundle `evidence/rver/okapi-wave/`:

- **control wave**: tenant migrated legacy→port behind real Okapi; catalog and
  row counts unchanged; changelog EXECUTED=0 / MARK_RAN=14; smoke via Okapi;
- **forced-failure wave**: 3 tables dropped pre-wave → the lane's
  post-verify-catalog gate FAILs, run ABORTs, **automatic Okapi rollback
  re-verified** (routing lists 4.4.0 again), legacy data untouched;
- **repair + resume**: completes in a fresh run dir; the failed run's 15
  evidence files re-hash byte-identical (append-safe evidence).

Review №5 re-drove the whole wave independently ("all ten gates passed") and
additionally sabotaged the artifact identity (F-53 — a lane hardening item,
not a data-integrity defect).

### 1.4 Destructive operations are strictly gated

**Claim:** the port cannot destroy tenant data on ambiguous input — it is
*stricter* than legacy here, by confirmed decision.

**Pin:** purge requires an explicit boolean `purge:true` discriminator on the
wire; the R-VER purge matrix (`evidence/rver/purge-matrix/`, **19/19**)
proves coercible scalars (`"true"`, `1`, `1e0`, `0.0`), duplicate keys in both
orders, arrays, structured values, and unicode-escaped key spellings are all
rejected 400 `purge.not.explicit` with the schema provably intact between
rejections, while explicit `purge:false` disable is side-effect-free. Review
№5 extended the matrix with further hostile shapes; all rejected.

### 1.5 In-memory state is coherent across tenant lifecycle

**Pin:** `TenantDisableCacheEvictionIT` (**3/3**) proves tenant disable evicts
the widget-definition cache, so a re-enabled tenant cannot see stale data.
Review №5: "behaviorally non-vacuous; 3/3".

## 2. Functional parity

### 2.1 The continuous wire-parity harness: three-leg capture + diff

The primary parity instrument is `docs/migration/harness/` — a **45-probe**
corpus (`probes.tsv`) spanning dashboards, widgets, number generators (all
check-digit algorithms and `${current_year}` templating), refdata, RFC 8693
attestation, tenant lifecycle, error shapes, paging, sorting, and filtering.
`capture.sh` runs it against a populated tenant (`populate.sh`) on each side;
`diff-runs.sh` compares per-probe bodies with an explicit allowlist
(`deviation-allowlist.tsv`) so only *registered* differences may pass.

Latest committed run (`evidence/rver/harness/`, three legs — legacy, port
fresh-DDL, **and the port serving an adopted legacy tenant**):

| Leg pair | Result |
|---|---|
| legacy vs port-adopt-legacy | **exit 0 — byte-equal 21, sorted-equal 9 (JSON array order only, D-4), allowed 14 (all registered), diverged 0** |
| all three capture manifests | `failed_probes: []`, every body sha256-pinned (132/132 recomputed in `forensics.md`) |

Review №5 re-ran all three legs fresh at 45 probes with the same verdict
(23/7/15/0 under its census). The earlier 28-probe bundle (`evidence/r18/`,
84/84 bodies) shows the same result at M-phase midpoint — parity held across
the whole remediation arc, not just at the end.

### 2.2 Parser semantics proven against the *booted* legacy module, bug-for-bug

The riskiest parity surface — the kiwt listing grammar (`filters=`, `match=`/
`term=`, escapes, `%`/`_` wildcards) — was not ported from reading legacy
source but from **legacy-as-oracle matrices**: probe corpora executed against
the running legacy module, with the port then made to match observed behavior
(decision DP-1(a): bug-for-bug parity).

**Pins:** `evidence/r31-escaped-oracle/`, `evidence/r32-wildcard-oracle/`
(matrix derivations) and `evidence/rver/oracle-rerun/` (both sides live at
closing HEAD): **41 probes, 39 semantically EQUAL**, including:

- raw-literal escape semantics, operator-spelling absorption, structural-token
  voiding (the escaped-token matrix);
- live `%`/`_` wildcards, escaped literals, `=i=` unwrapped ilike;
- the **positive proof of a reproduced legacy bug**: legacy's broken
  `([^\\])% → $1 + literal "$2"` wildcard transform means `code=~ab%cd`
  matches a row literally named `ab$2cd` — a row was planted and **both sides
  match it over the wire** (probe `w1`).

The 2 non-equal probes are the registered deviations D-33 and D-34 (below).
Review №5 re-derived the matrices independently and confirmed, calling DP-1(a)
fidelity "demonstrated for the directed matrix".

### 2.3 Every known behavioral difference is registered and continuously re-verified

`docs/migration/wire-compat-deviations.md` is the closed dossier: **34
deviations (D-1..D-34)**, each with legacy behavior, port behavior, impact
analysis, and evidence pointers, in six sections:

| Section | Content |
|---|---|
| A | Legacy defects **not** ported — port behaves correctly on purpose (e.g. legacy uncaught 500s → governed 4xx) |
| B | Nondeterminism pinned down (array ordering, timestamps) |
| C | Error-envelope shapes |
| D | Request-binding differences |
| E | Listing-parameter coverage |
| F | Platform surface and schema residue (e.g. D-34: Tomcat 9 lenient vs Tomcat 10.1 strict URI decoding — affects only RFC-violating clients; review №5 verified the official `ui-service-interaction` UI encodes correctly) |

Deviations that surface in harness probes are wired into
`deviation-allowlist.tsv`, so any *unregistered* divergence fails the harness.

### 2.4 Module lifecycle parity through a real Okapi

Enable (fresh and adoption shapes), upgrade (`module_from`/`module_to`),
disable, purge, reference-data seeding (`loadReference`), and routing all
proven against real Okapi in the rollout lane (§1.3) plus `TenantEnableIT` /
the tenant-enable bodies in `evidence/rver/harness/`. The ModuleDescriptor —
interfaces, permission set (`servint.<area>.<verb>`), endpoints — is
byte-identical across rebuilds (§ artifacts table) and was diffed against the
legacy descriptor during review rounds.

### 2.5 Platform surface

- **Management/actuator boundary** (`evidence/rver/management-probe.txt`):
  `/admin` exposes exactly health + metrics; loggers 404 including the
  unauthenticated TRACE-flip attack (F-43 reproduction) — verified over the
  wire on the rebuilt image.
- **Kubernetes template** (`K8sDeploymentTemplateTest` 4/4 + kubeconform
  strict, `evidence/rver/k8s-smoke.txt`): port routing, probes, and the
  Okapi-only NetworkPolicy render validly and match `server.port`.
- **Spec governance**: the SDD spec corpus (166 files: requirements, API
  contracts, behavior scenarios, ADRs, traceability) passes structural +
  semantic validation — **249/249 findings PASS**, with the M9-touched pairs
  live-judged by model, not cache-replayed (`evidence/semantic/rver/`).
  Review №5 independently evicted 5 cache entries and re-judged live: all PASS.

## 3. Independent review confirmation

| Review | Date | What it independently reproduced |
|---|---|---|
| №1–№2 | 2026-06/07 | initial claims audit; drove the oracle-first parser rework |
| №3 | 2026-07 | clean build, unmodified harness 28/28, real Okapi 7.0.6 lifecycle, populated adoption, semantic 241/241 |
| №4 | 2026-07 | purge/wildcard/proxy/continuity attack rounds; defined the nine closure gates |
| №5 | 2026-07-23 | **everything above at final HEAD**: rebuilt image, purge matrix, 41-probe oracle both sides incl. planted-row proof, 45×3 harness diff exit 0, ten-gate Okapi wave with forced failure + rollback, continuity ITs, semantic live re-judge, hash forensics, and a hunk-by-hunk minimal-diff audit (every `src/main` change traced to a finding or confirmed decision) |

Review №5's verdict is NO-GO for *release* — but explicitly **not** on parity
or integrity grounds: "The strict purge discriminator, bug-for-bug wildcard
transform, … three-leg harness, semantic gate, and real-Okapi rollback/resume
lane all reproduced successfully. This review does not retract those results."
The blocking findings (F-46..F-53) are two malformed-input parser edges and
meta-assurance items (test/pin strength, base-image pinning, lane artifact
identity).

## 4. Honest boundary of the evidence

Registered limits — stated here exactly as tracked in
`completion-report.md` and the review reports:

| Not proven | Status |
|---|---|
| Terminal-backslash filter edge (F-46/D-35 candidate) | legacy silently drops the filter; port applies it correctly — unregistered divergence at a malformed-input edge, pending decision (parity vs governed D-35) |
| NUL-byte filter edge (F-47/D-36 candidate) | legacy 500; port 409 / 200-unfiltered for pure NUL — pending governed handling + D-36 |
| Bit-for-bit row *content* identity | row counts + catalog proven; full content diff never run |
| Fresh-schema full table census | spot pins only (F-52 tracks the overclaim) |
| Production-scale load/soak, multi-replica key creation, 24-hour timer fire, hosted SBOM | unprobed, listed as residuals |
| Exhaustive grammar fuzzing | matrices are directed, not fuzzed — F-46/F-47 are exactly this residual materializing |

Everything else in sections 1–2 is pinned by a committed test or capture that
re-runs; a regression in any of it fails a build, a harness diff, or a lane
gate rather than surviving silently.

## 5. Reproducing the evidence yourself

```bash
# full build + all continuity/parity ITs (needs Docker for Testcontainers)
mvn -B clean verify

# three-leg wire harness (legacy jar on :8080, port on :8081, see harness/README.md)
docs/migration/harness/capture.sh legacy && docs/migration/harness/capture.sh port
docs/migration/harness/diff-runs.sh <legacy-run> <port-run>

# real-Okapi rollout rehearsal (control + forced-failure + rollback + resume)
LANE_OUT=/tmp/lane docs/migration/harness/rollout-lane.sh

# spec structural + semantic gate (run from repo root)
sdd validate --semantic --branch main-final
```

Full per-gate instructions: `docs/migration/harness/README.md` and
`docs/migration/evidence/rver/README.md`.
