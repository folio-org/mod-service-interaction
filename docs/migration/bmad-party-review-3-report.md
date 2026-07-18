# BMAD Party Review №3 — M7 migration final validation

**Review date:** 2026-07-21  
**Reviewed application range:** `0d7dd33..950764a`  
**Review checkout:** `b5e1a076bbfc1c5c64281305bb776448b7ddd562` (application at `950764a`, plus this mission prompt)  
**Decision:** **NO-GO for production cutover**

## Executive verdict

M7 closes a substantial part of review №2. The clean build passes, the rebuilt Java 21 image boots, the unmodified legacy/port harness is green, the 20-client first-use burst is fixed, the real Okapi 7.0.6 lifecycle works, populated adoption survives, the semantic gate is 241/241 PASS, the cache remains bounded under a 2,000-tenant stress, and both drift-gate mutations fail compilation.

The release still cannot cut over safely. The checked-in `rollout.sh` can record a tenant and wave as PASS without changing Okapi routing; a reproduced control wave remained routed to legacy. Retrying a failed tenant also destroys evidence referenced by the append-only ledger. The parser remediation introduced an unregistered compound-filter deviation and two other empty-RHS contract violations. The runbook's manual timer verification is false on the tested Okapi, and an omitted `purge` flag remains a destructive, schema-valid footgun.

Final tally for new findings: **1 Blocker, 8 Major, 2 Minor**. Review №2's F-21 is **REGRESSED**; F-12, F-13, F-16, F-22, and F-24 remain only **PARTIALLY CLOSED**.

## Review method and independent evidence

Party Mode roles were split across Product/Analyst, Architecture, QA, Security, SRE, and Technical Writing. Three additional independent code-review layers ran as Blind Hunter, Edge Case Hunter, and Acceptance Auditor. All application experiments used throwaway checkouts and isolated containers; the origin was not mutated during testing.

| Evidence | Independent command/probe | Result |
|---|---|---|
| E1 — clean build | `mvn -B verify` in a clean checkout | `BUILD SUCCESS`; 3 unit + 89 integration tests; 0 failures/errors/skips; module descriptor validator passed |
| E2 — release image | two independent root `docker build --no-cache`/`--pull` builds from HEAD | Java 21; `/admin/health` 200/UP; image IDs `15eaba…365cd` and `d8413d…c77a9` |
| E3 — unmodified harness | `populate.sh` → `capture.sh legacy` → adopted port → `capture.sh port` → `diff-runs.sh` | 28/28 each side; exit 0; byte-equal 13, sorted-equal 6, allowed 9, diverged 0 |
| E4 — pool stress | 20 simultaneous first-use `getNextNumber` calls, default 10-connection pool | 20×200; 20 unique gapless values `000000001..000000020` |
| E5 — real Okapi | Okapi 7.0.6 install → upgrade → disable → re-enable → purge | all control-plane calls 200; delivered D-26 bodies matched; disable preserved data/changelog; purge removed schema |
| E6 — adoption | restore committed `r13b-populated-schema.sql`, upgrade over wire | 204; `EXECUTED=0`, `MARK_RAN=14`; sampled business counts unchanged; readback passed |
| E7 — semantic gate | `sdd validate --semantic --branch main-final --format json` | 165 files, 0 errors/warnings; gate PASS; 241 PASS, 0 SUSPECT/FAIL/UNKNOWN |
| E8 — retained evidence | recompute manifests/bodies and replay `diff-runs.sh` | 107/107 response hashes matched; R18 84/84; diff replay exit 0; R20 five bodies/headers matched proxy logs |
| E9 — drift mutations | delete `AttestationController#getAttestationToken`; separately add `getDriftProbe` to its spec | both builds failed with the expected missing abstract method |
| E10 — cache stress | scratch change `SIMULATED_TENANTS=2_000`; targeted `WidgetCacheCapacityIT` | 3/3 pass; bound/eviction/meters held; process max RSS 843,844 KiB |
| E11 — rollout | `bash -n`, two-tenant DRY_RUN, real two-tenant wave, forced failure, resume, rollback/re-cutover | mechanics ran, but control-plane/evidence defects below reproduced |
| E12 — parser attacks | booted legacy + rebuilt port compound probes; two scratch regression tests | F-26 reproduced dynamically |

## Closure matrix

Statuses apply the mission's strict rule: the original reproduction was rerun and its claimed pin inspected/executed.

| Item | Final status | Reproduction and pin |
|---|---|---|
| F-02 tenant lifecycle | **CLOSED** | E5 real Okapi lifecycle plus direct shaped disable: 204, 14→14 changelog, custom row 1→1, deleted seed stayed deleted. `TenantEnableIT` 5/5. |
| F-11 evidence harness | **CLOSED** | E3 ran the unmodified harness end-to-end on independently booted sides; 28/28 + 28/28, no unexplained divergence. |
| F-17 pool starvation | **CLOSED** | E4 reproduced the exact 20-client/default-pool failure mode; all calls now queue and succeed gaplessly. `NumberGeneratorConcurrencyIT` 2/2. |
| F-18 disable model / D-26 | **CLOSED** | E5 captured fresh Okapi 7.0.6 install/upgrade/disable/re-enable/purge bodies and database effects. |
| F-19 D-2 oracle | **CLOSED** | Fresh legacy widget-definition create 201; duplicate 500; port 405. D-2 and pins agree. |
| F-20 dashboard 200→201 | **CLOSED** | Unmodified population observed legacy 200 and port 201; D-27 and per-side pins exercised. |
| F-21 parser/error completeness | **REGRESSED** | Six directed rows and D-28..D-31 hold, but the R16 empty-RHS implementation causes F-26, absent from D-1..D-31 and the harness. |
| F-22 auditable evidence | **PARTIALLY CLOSED** | E8 proves all committed response hashes. The claimed port/legacy binaries and image are absent; the claimed port JAR hash cannot be recreated because JARs are timestamp-nondeterministic; semantic finding IDs/verdicts are absent, so “bundle alone” is false. |
| F-23 cache growth | **CLOSED** | Checked-in 600-tenant IT 3/3 plus E10 at 2,000 tenants. The 500-entry bound, explicit eviction, and meters held. Active eviction/real 30-minute expiry remain unprobed, listed below. |
| F-24 rollout/ledger | **PARTIALLY CLOSED** | Syntax, dry-run, real ≥2-tenant wave, stop condition, module-side disable, and resume mechanics work. F-27/F-28/F-29 show that actual cutover, immutable audit, and fail-safe recovery do not. |
| F-25 drift gate | **CLOSED** | E9 watched both required mutations fail compilation. The separate tenant-spec scope gap is F-33. |
| F-04 listing/int64 residue | **CLOSED** | `nextValue>5` returned exactly 6, 9, 2147483649 on both sides; paging/sort pins and grammar IT ran. F-26 is a new R16 regression. |
| F-05 first-use residue | **CLOSED** | E4 plus the concurrency IT. |
| F-10 DDL/adoption | **CLOSED** | E6, `AdoptedSchemaUpgradeIT` 8/8, and `FreshDdlCatalogIT` 4/4. The test's “bit-for-bit” prose is broader than its assertions and is recorded under coverage/dissent. |
| F-12 runbook | **PARTIALLY CLOSED** | Backup/restore, downgrade, readback, re-cutover, and lifecycle work; Phase 4 control-plane semantics and Phase 5 timer instructions are defective. |
| F-13 trace/spec honesty | **PARTIALLY CLOSED** | `TRC-023` still says populated adoption is “not yet by an in-suite IT” and cites only `TenantEnableIT`, despite the new adoption/fresh-DDL ITs. |
| F-14 error/listing deviations | **CLOSED** | Original malformed JSON, missing tenant, invalid Boolean, bad numeric query, and duplicate-code cases were rerun and governed. F-26 is separate new behavior. |
| F-15 semantic evidence | **CLOSED** | E7 independently reproduced 165/0/0 and 241 PASS/0 others. The retained-bundle limitation remains F-22. |
| F-16 high-risk coverage | **PARTIALLY CLOSED** | Suite grew and all 92 tests pass, but it misses F-26's three empty-RHS cases, control-plane rollout, immutable retry evidence, destructive omitted-purge rejection, and cache eviction on disable. |

## Seven must-fix items from review №2

| # | Requirement | Verdict | Evidence |
|---|---|---|---|
| 1 | Remove nested-transaction starvation and cover pool size | **MET** | E4; concurrency IT. |
| 2 | Correct/test real Okapi disable; align ADR/REQ/TRC/D-17/D-26 | **MET** | E5 and D-26 lifecycle. The destructive complement is F-32. |
| 3 | Correct D-2; register D-27/parser deviations; rerun harness | **PARTIALLY MET** | D-2/D-27..D-31 and E3 pass; new D-32/F-26 is unregistered. |
| 4 | Executable, append-safe rollout/abort/rollback; descriptor/year fixes | **NOT MET** | F-27 through F-31. |
| 5 | Retain auditable captures, diffs, digests, semantic verdict evidence | **PARTIALLY MET** | Response hashes are sound; exact binaries/verdict corpus/query provenance are not bundle-verifiable. |
| 6 | Populated-adoption regression and fresh-DDL catalog assertion | **MET** | E6 and both named ITs. |
| 7 | Bound/measure definition cache and enforce drift gate | **MET** | E9/E10. |

## Nine review №1 reconsideration conditions

| # | Terminal verdict | Evidence pointer |
|---|---|---|
| 1. Java 21 release path, descriptors, SBOM | **PARTIALLY MET** | E1/E2 and descriptor validation pass; the hosted SBOM workflow itself was not executed in this review. |
| 2. Published lifecycle/seeding through real Okapi | **MET** | E5 reproduced all lifecycle transitions and bodies on Okapi 7.0.6. |
| 3. 64-bit safety, atomic first use, regressions | **MET** | E3/E4 plus boundary and adoption ITs. |
| 4. Full listing/error contract governed with consumer evidence | **NOT MET** | F-26 is an unregistered behavioral difference and spec violation. |
| 5. Tenant cache/key lifecycle/display identity | **MET** | Cache isolation/capacity, key expiry/purge rotation, and display identity ITs all passed. Multi-replica first-key creation remains unprobed. |
| 6. Fresh DDL parity and honest adoption verification | **MET** | E6 plus D-16 and fresh/adopted ITs. |
| 7. Executable canary/rollout/backup/abort/resume/timer/rollback | **NOT MET** | F-27 through F-31. |
| 8. Semantic proof and honest traceability | **PARTIALLY MET** | E7 passes; F-13/F-22 residue remains. |
| 9. Empirical matrix against the built release artifact | **MET** | E2–E6 and E11 used rebuilt HEAD artifacts, not the stale R18 image. |

## New findings register

### F-26 — Major / proposed D-32 — empty-RHS handling is not clause-local and changes results

**Observed:** `KiwtListing` resolves joins and recursively builds all compound children before `DroppedClauseException` is caught around the top-level filter. Three failures result:

- `code==alpha&&prefix==` and `(code==alpha||prefix==)` returned legacy HTTP 200/count 0 but port HTTP 200/all 12 rows; each component alone was parity-clean.
- `checkDigitAlgo.value==` should be dropped, but the abandoned INNER JOIN excludes null-associated rows. A scratch IT comparing it to the unfiltered listing failed.
- `notAProp==` should be dropped under REQ-022 AC1, but a scratch IT received 400 `invalid.property`.

**Reproduction excerpt:**

```text
filters=code==alpha&&prefix==      legacy 200 []    port 200 count=12
filters=(code==alpha||prefix==)    legacy 200 []    port 200 count=12
mvn -Dit.test=KiwtListingGrammarIT verify
... emptyRightSideOnNullableAssociationIsActuallyDropped ... FAILURE
... notAProp== ... expected 200 but was 400
```

**Location:** `src/main/java/org/folio/servint/web/KiwtListing.java:132-193`; `specs/requirements/listing-grammar.yaml:32-49`.  
**Action:** decide the governed compound semantics from booted legacy, then make invalid/empty leaf handling side-effect-free and clause-local; add all three cases to the IT, harness, and deviation register if behavior deliberately differs.

### F-27 — Blocker — rollout PASS does not mean Okapi cutover

**Observed:** `rollout.sh` calls the port module directly, smokes it directly, then appends PASS. In a real Okapi control wave, the script printed success while `/_/proxy/tenants/row3ctl/modules` still listed only the legacy module; an Okapi GET hit the legacy logging proxy. The runbook leaves the actual Okapi install to an unchecked manual step after the wave.

```text
rollout: tenant row3ctl complete
rollout: OK — every tenant in wave 'control-wave' passed
GET /_/proxy/tenants/row3ctl/modules -> mod-service-interaction-4.4.0-SNAPSHOT
Okapi-proxied GET -> legacy proxy
```

**Location:** `docs/migration/harness/rollout.sh:282-303,445-481`; `docs/migration/cutover-runbook.md:378-407`.  
**Action:** make Okapi install the checked cutover step, verify installed module/routing, smoke through Okapi before PASS, and make rollback perform and verify the inverse control-plane transition.

### F-28 — Major — retry destroys evidence referenced by the append-only ledger

**Observed:** evidence uses `$OUT_DIR/$tenant`, without wave/run identity. A forced failure created a 4,625-byte `catalog.diff` (`sha256 db2876…c533c`) referenced by the ledger. Retrying the same tenant in the same output directory overwrote snapshots/responses and deleted that diff; the immutable ledger row now points to missing/replaced evidence.

**Location:** `docs/migration/harness/rollout.sh:225,293-325`.  
**Action:** use immutable wave/run/tenant directories, hash evidence into the ledger, and refuse reuse.

### F-29 — Major — rollout failure handling can exit or continue after an unrecorded mutation

**Observed:** the ledger is first written after tenant mutation, but its parent/writability is not preflighted; `set -e` can exit without audit or rollback. A rollback HTTP failure does not set `HARD_STOP`, so `MAX_FAILURES>1` can proceed to more tenants. Curl has no connect/total timeout, and a committed enable followed by a lost response is treated as “not enabled,” skipping reconciliation/disable. DRY_RUN also certifies malformed JSON configuration:

```text
MODULE_TO='mod"bad' TENANT_PARAMETERS=not-json ... rollout.sh bad-json tenantok
--data {"module_to":"mod"bad","purge":false,"parameters":not-json}
rollout: OK — every tenant in wave 'bad-json' passed
```

**Location:** `docs/migration/harness/rollout.sh:140-179,282-303,387-467`.  
**Action:** validate JSON and destinations up front; preflight an append; add network/database timeouts and ambiguous-result reconciliation; hard-stop on rollback failure.

### F-30 — Major — tenant-file normalization can target or misclassify the wrong schema

**Observed:** every whitespace character is removed rather than trimming only edges, so `row 3ctl` silently becomes `row3ctl`. The regex allows a 40–63-character tenant even though the 24-character schema suffix leaves only 39 characters under PostgreSQL's 63-byte identifier limit; existence/catalog checks use the untruncated name and can classify a populated tenant as fresh.

```text
TENANTS_FILE: "row 3ctl"
rollout: --- tenant row3ctl (schema row3ctl_mod_service_interaction) ---
40-character tenant -> accepted; schema expression is 64 characters
rollout: OK — every tenant in wave 'edge-wave' passed
```

**Location:** `docs/migration/harness/rollout.sh:117-135,223-225`.  
**Action:** trim only outer whitespace, reject internal whitespace/duplicates, and enforce the actual derived-schema length.

### F-31 — Major — runbook treats a healthy timer registration as failure

**Observed:** real Okapi 7.0.6 listed the `_timer` registration, but the runbook's exact manual `POST /servint/numberGenerators/resetYearSequences` through Okapi returned 404 `No suitable module found`; that route is in the system `_timer` interface, not the public `servint` handlers. Direct-module POST returned 200/currentYear 2026. The runbook tells operators to abort on this healthy state.

**Location:** `docs/migration/cutover-runbook.md:412-437`.  
**Action:** use the `/timers` entry plus observed scheduled execution/log, or deliberately expose a routable manual endpoint; remove the false 404 diagnosis.

### F-32 — Major — omitted purge is a destructive, schema-valid default

**Observed:** on a disposable enabled tenant, `POST /_/tenant` with `{"module_from":"...","module_to":""}` and no `purge` returned 204 and dropped the schema. The same occurred with omitted `module_to`. Okapi 7.0.6 always sent an explicit Boolean, and ADR-012/D-26 document the nuance, but the local API schema permits it and deletion is fail-open.

**Location:** `specs/api/servint-tenant.yaml:34-41`; `specs/decisions/012-tenant-interface-2-0.yaml:24-26,78-81`; `src/main/java/org/folio/servint/controller/ServintTenantController.java:21-36`.  
**Action:** require explicit `purge:true` for deletion and reject blank/omitted `module_to` with absent/null purge; model lifecycle bodies as validated alternatives.

### F-33 — Major — compile drift enforcement excludes the tenant API while documentation claims all APIs

**Observed:** the README calls `specs/api/*.yaml` the build-consumed single source of truth, but `servint-tenant.yaml` is intentionally excluded from the six generator executions. The two watched mutations prove the six generated surfaces, not this highest-risk lifecycle surface.

**Location:** `README.md:25-47`; `pom.xml:36-45`.  
**Action:** either generate/compatibility-check the tenant contract against folio-spring's API or narrow the claim and add an explicit tenant spec/runtime completeness gate.

### F-34 — Major — rehearsal evidence proxy records credentials verbatim and can fail after mutation

**Observed:** `proxy.py` stores every request header in JSONL and per-call header files, including tokens/authorization/cookies, in artifacts designed for retention. It forwards upstream before writing evidence; ENOSPC/unwritable output can turn a completed tenant mutation into a client error and invite a retry with missing proof.

**Location:** `docs/migration/evidence/r20-rehearsal/proxy.py:58-110`.  
**Action:** redact sensitive headers, preflight/atomically write evidence, and make post-forward logging failure explicit without misrepresenting the upstream result.

### F-35 — Minor — recorded fallback rollback command is not executable

**Observed:** when optional `OKAPI_URL` is unset, the ledger single-quotes literal `$OKAPI_URL`; copy/paste with the variable later set returned curl error 3 / bad hostname. The command also omits an Okapi authorization token for secured deployments.

**Location:** `docs/migration/harness/rollout.sh:389-405`.  
**Action:** require Okapi recovery configuration for real runs and render a validated, authenticated command.

### F-36 — Minor — cache metrics are registered but not operationally exposed

**Observed:** the capacity IT finds `cache.size` and `cache.evictions` only through the in-process `MeterRegistry`; Actuator exposes only `health,loggers`. No checked exporter makes the advertised cache signals scrapeable.

**Location:** `src/test/java/org/folio/servint/WidgetCacheCapacityIT.java:131-143`; `src/main/resources/application.yml:43-48`.  
**Action:** configure a supported metrics exporter or expose the metrics endpoint under the deployment's security model, then test it.

## Coverage map

| Area | Verified | Explicitly unprobed / limited |
|---|---|---|
| Build/release | Clean Maven verify, descriptor validator, two rebuilt/booted Java 21 images | Hosted CI/SBOM action and registry publication |
| Wire | Unmodified 28-probe matrix, directed D-28..D-31, exploratory F-26, malformed headers/errors covered by IT/harness | Exhaustive all-method/all-content-type/header permutation; every consumer |
| Transactions/scale | 20-client first-use burst; 2,000 simulated cache tenants | Generation/purge/year-reset interleavings; dashboard hot-path pool exhaustion; multi-hundred simultaneous real enables |
| Lifecycle | Real Okapi 7.0.6 full lifecycle, direct disable, purge complement | Other Okapi versions; disable/purge racing in-flight tenant requests |
| Data | Fixture restore, adoption gate, sampled counts/readback, backup/restore, legacy rollback/re-cutover | Cryptographic before/after row-content census for every owned table |
| Security | Attestation RS256/kid/tenant/expiry/key-rotation ITs; cross-tenant cache ITs; Criteria property resolution inspected | Multi-replica first-key creation; direct-module trust boundary/credentialed production proxy; broad fuzzing/ReDoS |
| Cache | 600 and 2,000 tenants, size/eviction meters, tenant isolation | Real 30-minute expiry, active-tenant eviction race, exporter/context-refresh behavior |
| Specs/evidence | Fresh 165/241 semantic pass, drift mutations, all response hashes | Bundle-only reconstruction of exact binaries/verdict corpus; tenant OpenAPI compile linkage |
| Operations | Real wave, failure/abort/resume, backup/rollback/re-cutover | Mid-upgrade process kill, stalled dependency, disk-full ledger/proxy in a production-like rig, actual 24-hour timer firing |

## Dissent preserved

1. **Rollout scope.** The strongest defense is that the runbook openly calls `rollout.sh` a module-direct verification pass and instructs a later Okapi install. The opposing position is stronger: the ledger claims which module each tenant is on, calls the wave complete, and neither executes nor verifies the only operation that changes production routing. Final position: F-27 is a Blocker.
2. **F-21 status.** A narrow reading marks the six original directed rows CLOSED and carries F-26 separately. QA calls it REGRESSED because the R16 fix itself introduced the compound failure. Final position: REGRESSED; either label still yields NO-GO.
3. **F-22 evidence.** Artifact defenders argue Git plus a fresh rebuild/re-run is adequate provenance. The opposing literal reading notes the explicit “bundle alone” claim cannot recreate absent binaries, semantic finding IDs, or several database-query proofs. Final position: PARTIALLY CLOSED, not evidence corruption.
4. **Omitted purge.** Architecture notes that it is governed and unreachable from observed Okapi 7.0.6 traffic. Security/SRE reject omission as consent for total data deletion on a valid internal request. Final position: Major safety defect, not proposed D-32.
5. **Timer.** Registration itself is healthy; only the documented manual verification is false. Final position: F-31 is a runbook defect, not proof that scheduled execution is broken.
6. **Adoption test strength.** `AdoptedSchemaUpgradeIT` says “bit-for-bit” but asserts table names and counts for a subset plus limited wire readback. Independent restore evidence is positive; full row-content identity remains unprobed rather than failed.

## Final recommendation and merge gate

**NO-GO for production cutover.** Do not merge the migration PR as release-ready until, at minimum:

1. F-27/F-28/F-29/F-30/F-31/F-35 are resolved and the corrected tool is rerun through real Okapi with immutable evidence, forced failure, authenticated rollback, resume, and routing verification.
2. F-26 is fixed or explicitly governed as D-32, with all compound/dotted/unknown-property cases pinned in the IT and harness against booted legacy.
3. F-32 is resolved by an explicit-destructive-intent contract or formally accepted by the security/product owner with compensating controls.
4. The tenant API is brought under an explicit drift/compatibility gate (F-33).
5. The retained-evidence claim and `TRC-023` are corrected; exact semantic verdict evidence and sufficient artifact provenance are retained without secrets (F-22/F-13/F-34).

After those changes, repeat this review's real-Okapi control wave, parser attacks, evidence forensics, semantic gate, clean image/harness, and failure/recovery experiments. No application source or existing documentation was changed by this review; this report is the only repository write, per the user's explicit storage instruction.
