# Independent migration re-review №2 — post-M6 remediation

**Target:** 0d7dd33708a65528f007eaae60e1fbf2ef8ed223 on feat/migration-01  
**Remediation range:** d5ba8ea..1d298e0 — 19 commits, 94 files, 6,271 insertions, 787 deletions  
**Additional HEAD commit:** 0d7dd337 — review-prompt documentation only  
**Review date:** 2026-07-21  
**Mode:** BMAD Party Mode, independent Analyst, Architect, Developer/QA, Security, SRE, Technical Writer, and PM synthesis  
**Repository policy:** read-only; all generated evidence stayed under /tmp

## Final recommendation

**NO-GO for production cutover.**

M6 fixed substantial defects. A clean Java 21 image builds and boots, the full Maven suite passes, 64-bit values survive, the original listing probes now match, no-parameter seeding matches legacy, tenant cache isolation and display-data identity are fixed, fresh DDL no longer has the incorrect default, fresh semantic validation produces 238/238 PASS, and a storage-level backup/adoption/rollback rehearsal succeeded.

The cutover claim still fails under independent execution:

1. Twenty simultaneous first-use requests deterministically exhaust the ten-connection pool; the release image returned 15 HTTP 500 responses.
2. The governed _tenant 2.0 disable model is false. Okapi POSTs module_from without module_to; the framework treats purge=false as an update and runs Liquibase plus afterTenantUpdate. The regression named for disable sends no request.
3. The checked-in harness cannot populate a real legacy tenant because two expected statuses are wrong. A scratch-corrected run then finds an unregistered legacy-500/port-200 invalid-Boolean difference.
4. The new parser silently drops or reinterprets malformed, type-invalid, empty-RHS, and escaped filters in ways absent from D-1..D-25.
5. The fleet rollout section of the runbook is pseudocode, references undefined variables, and truncates its ledger on every wave.
6. The committed response-hash and semantic artifacts do not contain enough evidence to verify their advertised claims.

## Independently reproduced positive evidence

| Area | Result |
|---|---|
| Release | Empty Maven cache package succeeded; Docker --no-cache image 5074efdc… booted Java 21 on 8081; /admin/health returned 200 UP. |
| Official tests | mvn -B verify: 3 unit + 71 integration, 0 failures/errors/skips, BUILD SUCCESS in 01:07. |
| Semantic lane | sdd validate --semantic --branch main-final: 238 verdict files; PASS 238, SUSPECT/FAIL/UNKNOWN 0; exit 0. |
| Data boundaries | Legacy and port preserved nextValue=2147483648 and maximumNumber=4294967296; port read the legacy-written row unchanged. |
| Original listing probes | nextValue>5 returned exactly 6, 9, 2147483649 on both; perPage=0, perPage=101, and bad sort all returned 200 with matching sets. |
| Seeding | No-parameter enable on both modules yielded 2 categories / 6 values / 0 generators / 0 widget types. |
| Tenant isolation | Same provider id returned Alpha only to tenant A and Beta only to tenant B; one harvest per tenant. |
| Display identity | Legacy reproduced the cross-dashboard rewrite; port returned 422 dashboard.id.mismatch and left both rows unchanged. |
| DDL/adoption | refdata_category.internal column_default was NULL. Adopted catalog and 37 table-count rows were unchanged; 14 changesets were MARK_RAN. |
| Storage rollback | Backup/restore succeeded; the port wrote/generated a number and the rebuilt legacy module read and advanced it. |

## Closure matrix

The command/output excerpts below are from clean /tmp checkouts and booted artifacts. Full QA artifacts are in /tmp/msi-quinn-r2.yOUVFO and SRE artifacts in /tmp/mod-si-rowan2.2KSUai.

| Item | Verdict | Original reproduction re-run and claimed pin |
|---|---|---|
| F-01 release path | **CLOSED** | mvn -B -Dmaven.repo.local=/tmp/.../m2 -DskipTests package → BUILD SUCCESS; docker build --no-cache → Java 21 image; curl /admin/health → 200 UP. The checked-in workflow builds, boots, and probes that endpoint; both descriptors validated. |
| F-02 tenant lifecycle | **REGRESSED** | Direct _tenant 2.0 enable/status/purge returned 204/200/204 and TenantEnableIT 5/5 passed. However, its disable test performs no request. Official Okapi 2.0 sends POST /_/tenant with module_from and no module_to; purge=false follows update/seeding/cache-eviction, contradicting ADR-012/REQ-020/D-17. |
| F-03 64-bit DTOs | **CLOSED** | Identical HTTP writes/reads of 2147483648 and 4294967296 succeeded on both; adopted legacy row read unchanged by port. NumberGeneratorParityIT.sequenceValuesAbove32BitsSurviveTheWire ran. |
| F-04 kiwt/listing | **PARTIALLY CLOSED** | The four original probes now match and KiwtListingGrammarIT 12/12 passed. New directed probes still diverge on empty RHS, escaping, malformed grouping, invalid coercion, and invalid Boolean; see F-21. |
| F-05 first-use concurrency | **PARTIALLY CLOSED** | Two-client fresh-pair race passed 5/5 with values 1,2 and NumberGeneratorConcurrencyIT passed. Pool-size stress returned port 5x200/15x500; see F-17. |
| F-06 seeding trigger | **CLOSED** | Fresh no-parameter legacy and release-image enables both produced 2/6/0/0. TenantSeedingMatrixIT.noParametersSeedsOnlyTheDefaultsBaseline and the true/false matrix ran. |
| F-07 widget cache isolation | **CLOSED** | The original two-tenant/same-provider-id request sequence returned Alpha/Alpha and Beta/Beta with one harvest per tenant. Three WidgetCacheTenantIsolationIT regressions ran. Capacity regression is separate F-23. |
| F-08 signing-key lifecycle | **CLOSED** | Targeted suite ran cachedKeyExpiryIsHonoredOnEveryRead, purge/re-enable rotation, stable row-id kid, and overlapping successor behavior. JVM-local compute is sound; multi-replica first creation remains unproven and lowers condition 5. |
| F-09 display-data identity | **CLOSED** | Original orphan-target request: legacy 200 and rewrote ddd_dash_id; port 422 and no mutation. Three DisplayDataIdentityIT regressions ran. |
| F-10 DDL/adoption claim | **PARTIALLY CLOSED** | Fresh catalog query confirmed column_default IS NULL and adopted catalog/count diffs were empty. No repository test asserts column_default or the full adopted-schema branch; ordinal difference remains governed. |
| F-11 evidence harness | **REGRESSED** | Fail-closed checks exist, but unmodified populate.sh exits at legacy dashboard create: expected 201, actual 200. Scratch correction reaches D-2 then exits: expected 500, actual 201. A second correction completes captures but diff-runs exits nonzero on invalid Boolean 500→200. |
| F-12 runbook | **PARTIALLY CLOSED** | Backup/restore, adoption, smoke, timer endpoint, and underlying legacy rollback were executed successfully. Phase 4 cannot run verbatim and loses prior-wave evidence; real Okapi control-plane rollback/timer registration was not exercised. |
| F-13 trace/spec honesty | **PARTIALLY CLOSED** | REQ-005 AC5 and TRC-019 improved. TRC-020 inherits the false disable model; TRC-023 claims adopted manifest evidence that is not committed; adopted-schema seeding is not pinned. |
| F-14 error/listing deviations | **OPEN** | Checked-in probe f14-invalid-boolean: legacy 500 vs release image 200, not allowlisted. ErrorEnvelopeMatrixIT instead asserts the false premise that legacy treats garbage Boolean as absent. Additional parser deltas are unregistered. |
| F-15 semantic evidence | **PARTIALLY CLOSED** | Fresh semantic command exited 0 with exactly 238 PASS verdict files. Committed semantic-validation.json contains structural 166/0/0 only, with no semantic verdicts, revision, or content digest. |
| F-16 high-risk regression coverage | **PARTIALLY CLOSED** | Full suite passes 3+71. It still omits pool-size concurrency, populated adopted-schema upgrade, the reproduced parser cases, real Okapi disable, executable batch rollout, and retained evidence verification. |
| Human issue 1 — API-first | **PARTIALLY CLOSED** | Inventory found 48 spec operations, 48 generated methods, 48 controller overrides, no current mismatch. All generated methods are Java default methods returning inherited 501, so the claimed compiler-enforced drift guard does not exist. |
| Human issue 2 — fresh + adopted DB | **PARTIALLY CLOSED** | Fresh provisioning tests pass and the independent adopted-schema rehearsal preserved catalog/rows. No committed adopted-schema IT creates a populated legacy schema and upgrades it; disable side effects are mis-specified. |

## Nine reconsideration conditions

| # | Verdict | Evidence |
|---|---|---|
| 1. Java 21 release image, both descriptors, root descriptor/SBOM validation | **PARTIALLY MET** | Clean package, descriptor validation, no-cache build and healthy boot succeeded. The SBOM workflow is checked in, but its produced SBOM content/publication was not independently executed or validated. |
| 2. Tenant lifecycle and parameter-driven seeding | **NOT MET** | Seeding matrix is positive, but actual Okapi 2.0 disable behavior contradicts the governed contract/test and no real-Okapi lifecycle rehearsal ran. |
| 3. 64-bit and atomic first-use with adopted/concurrency pins | **NOT MET** | 64-bit and two-client race pass; pool-size burst fails with connection starvation and adopted-schema pin is absent. |
| 4. Full listing/error contract governed with consumer evidence | **NOT MET** | F-14 remains open; D-2 is false; dashboard 200→201 and parser/error deltas are absent; D-19 consumer assertion has no retained evidence. |
| 5. Cache/key/display identity | **PARTIALLY MET** | Original functional defects are fixed at single-process scale. Widget cache is unbounded; multi-replica key creation and production heap ceiling are unproven. |
| 6. Fresh DDL and adoption verification | **PARTIALLY MET** | Runtime fresh default and adopted business catalog/count checks pass; no named regression pins the real populated-adoption path or column default. |
| 7. Executable fail-closed harness and runbook | **NOT MET** | Harness aborts on false legacy pins; Phase 4 contains placeholders/undefined variables and truncates rollout-ledger.csv. |
| 8. Semantic validation and trace evidence | **PARTIALLY MET** | Fresh 238/238 PASS succeeds; the committed artifact lacks semantic results/revision and TRC-020/023 overclaim proof. |
| 9. Empirical release-artifact matrix | **PARTIALLY MET** | Reviewers booted the release image, ran original probes, and completed 23+23 captures only after scratch fixes. The checked-in flow fails and the matrix exposes an unregistered difference. |

## New findings register

### F-17 — Major — pool-size first-use requests starve the database pool

**Observed.** The controller holds an outer transaction while NumberGeneratorService opens REQUIRES_NEW. At ten concurrent outer requests, all ten Hikari connections are held while each request waits for a second.

**Reproduction.**

    20 simultaneous GETs against one fresh generator/sequence pair
    port:   5 x 200, 15 x 500; committed values 000000001..000000005
    legacy: 11 x 200, 9 x 500; committed values 000000001..000000011
    HikariPool-1: total=10, active=10, idle=0, waiting=10

A separate scratch IT raised the existing 4-thread load to 16 threads × 12 calls and failed after 30 seconds with CannotCreateTransactionException.

**Action.** Remove the controller-wide transaction from getNextNumber or eliminate the nested REQUIRES_NEW boundary; pin concurrency at or above pool size and define overload behavior.

### F-18 — Major / proposed D-26 — _tenant 2.0 disable is not a no-call lifecycle

**Observed.** ADR-012, REQ-020, tenant-disable.feature, D-17, and TenantEnableIT say a plain disable sends no module call. The [official Okapi guide at the reviewed commit](https://github.com/folio-org/okapi/blob/dd321ba3634bf9241f497cb3eb417f061c5b85d2/doc/guide.md#disabling) states that 2.0 disable POSTs /_/tenant with module_from and omits module_to. With purge=false, folio-spring runs createOrUpdateTenant, Liquibase, and afterTenantUpdate. The alleged test only checks that an existing schema remains.

**Reproduction.**

    codegraph node disableWithoutPurgeLeavesSchemaIntact
    -> no HTTP request; only assertThat(schemaExists()).isTrue()

    Okapi body: module_from present, module_to omitted, purge false
    framework path: createOrUpdateTenant -> afterTenantUpdate

**Action.** Register D-26; correct ADR/REQ/feature/exemption/D-17/TRC-020; add an Okapi-shaped disable POST test that asserts schema, rows, Liquibase, seeding, and cache side effects; rehearse through real Okapi.

### F-19 — Major — D-2 and the legacy harness oracle are factually wrong

**Observed.** D-2 and populate.sh say legacy widget-definition POST creates a row then returns 500. Two clean-tenant runs against a freshly built legacy jar returned 201 with the created body and no NoSuchMessageException. Port remains 405.

**Reproduction.**

    checked-in populate.sh after scratch-only dashboard correction
    widget-def-create expected HTTP 500, got 201

**Action.** Correct D-2 to legacy 201 vs port 405, reassess direct-module consumers, change side-specific pins, retain the responses, and rerun both sides.

### F-20 — Major / proposed D-27 — dashboard creation changes 200 to 201 without a deviation

**Observed.** The harness assumes 201 on both sides. Fresh legacy returns 200 from its custom respond call; the governed port returns 201. D-1..D-25 contain no entry.

**Reproduction.**

    checked-in populate.sh on clean tenant
    dashboard-create expected HTTP 201, got 200

**Action.** Register D-27 with consumer analysis, split harness expectations by side, and pin legacy/port statuses.

### F-21 — Major / proposed D-28..D-31 — parser and error semantics remain incomplete

**Observed.** Directed differential probes against the two booted modules found unregistered behavior:

| Filter | Legacy | Port |
|---|---|---|
| enabled==notabool | 500 | 200 [] |
| nextValue== | 200, all 12 | 200, 0 |
| code==pl\\ain | 200, 0 | 200, one plain row |
| (code==plain | 500 | 200, all 12 |
| nextValue>notanumber | 500 | 200, all 12 |
| 10k characters | 400 empty | 400 with 435-byte body |

Values are Criteria-bound, so no SQL injection was observed; the defect is silent clause dropping/coercion and incomplete governance.

**Action.** Reject malformed/type-invalid filters consistently or register each intentional deviation; define escaping and empty-RHS semantics; extend the differential grammar and error matrix.

### F-22 — Major — committed certification evidence is not independently auditable

**Observed.**

- r12-port-capture-manifest.json references 23 response files; none is committed. Probe-list hash matches, but the body hashes cannot be recomputed. It has no image digest and is port-only.
- semantic-validation.json has 166 structural files and zero structural errors/warnings, but no semantic verdict records/counts, Git revision, or content digest. It cannot itself substantiate PASS=238.

Fresh QA reproduced all 23 statuses and self-verified fresh body hashes, but only 6/23 body hashes matched the committed manifest because ids/timestamps/datasets differ.

**Action.** Commit normalized response bodies, both manifests, diff output, image/jar digests, HEAD, exact commands, and the semantic verdict summary or immutable verdict bundle.

### F-23 — Major — repaired tenant cache has unbounded retained growth

**Observed.** tenantCaches has no size, TTL, or weight bound. A scratch probe fetched 500 distinct tenants and observed size 500; evicting one tenant reduced it only to 499. Entries retain harvested definition payloads until upgrade, purge, or restart.

**Action.** Use a bounded/weighted cache, export size/eviction metrics, define capacity, and run a multi-hundred-tenant heap/load test. The fixed tenant isolation remains valid.

### F-24 — Major — batch rollout is not executable and can lose its audit ledger

**Observed.** Runbook Phase 4 contains literal omitted steps, uses undefined CAT/CNT/SMOKE variables, enables the tenant before omitted validation, and executes : > rollout-ledger.csv for each wave. Phase 0 also names @ModuleDescriptor.json although Maven emits target/ModuleDescriptor.json.

**Action.** Replace pseudocode with a checked-in executable, append-safe ledger, explicit pre/post capture and smoke calls, return-code checks, stop conditions that include catalog/count failures, and automatic failed-tenant rollback.

### F-25 — Minor — generated default methods do not enforce spec-to-controller drift

**Observed.** Current inventory is complete: 48 spec operations, 48 generated methods, 48 overrides. All generated API methods are default methods with a 501 fallback, so a newly generated operation can compile without an implementation.

**Action.** Add a CI route/override completeness check or generate abstract methods; correct README/completion-report wording.

## Coverage map

| Dimension | Probed | Still unprobed / bounded |
|---|---|---|
| Release | Empty Maven cache, clean image build/boot/health, descriptor generation/validation | Actual GitHub/Jenkins publication and produced SBOM contents |
| Wire | Original F-03/F-04/F-07/F-09, 23-probe matrix, 14 directed parser cases | Exhaustive grammar/property/coercion matrix; every permission; all clients |
| Data | Fresh/no-param seeds, 64-bit adoption, fresh default, populated adoption catalog/counts | Production-scale tenants; crash mid-Liquibase; every partially seeded legacy state |
| Concurrency | Two-client repeated race and 20-client burst | Multi-replica generation, purge-vs-generation, controlled load/latency envelope |
| Security/cache | Tenant isolation, key expiry/purge/kid, display identity, parameter binding | Multi-replica key creation; multi-hundred-tenant heap ceiling |
| Lifecycle | Direct-module enable/status/purge, storage rollback | Real Okapi install/disable/downgrade/purge and timer registration/firing |
| Operations | Backup/restore, canary-like adoption, smoke, timer endpoint, legacy readback | Executable multi-wave rollout, abort/resume, real control-plane rollback |
| Governance | Fresh structural+semantic validation, selected TRCs, route inventory | Auditable committed semantic/capture evidence; consumer evidence for D-19 and new deviations |

## Dissent preserved

1. **Release and storage readiness.** SRE considers the image and storage-level cutover/rollback healthy. QA/PM decline production GO because control-plane lifecycle and fleet rollout are not executable or proven.
2. **F-07/F-08 closure.** Security supports CLOSED for the original functional defects. Architecture limits production confidence because cache capacity and multi-replica key creation are unproven.
3. **Condition 7.** SRE calls the runbook partially met because backup/adoption/rollback worked. PM records NOT MET against the exact condition because the checked-in harness and batch rollout cannot complete verbatim.
4. **Semantic outcome versus evidence.** The live semantic lane genuinely passes 238/238. Documentation objects only to the committed artifact's inability to prove that result.
5. **Adoption.** Independent rehearsal preserved the tested schema and rows. The remaining objection is absence of a named populated-adoption regression and committed evidence, not a reproduced data-loss defect.
6. **High-load parity.** Both legacy and port fail at 20 clients, but the port fails more often through deterministic nested-transaction pool starvation. This is a port reliability defect even though it is not strict legacy perfection.

## Must fix before PR merge

1. Remove the nested-transaction pool-starvation path and add pool-size concurrency coverage.
2. Correct and test real Okapi 2.0 disable behavior; update ADR/REQ/TRC/D-17 and register D-26.
3. Correct D-2, register dashboard 200→201 and parser/error deviations, and rerun the unmodified harness.
4. Replace runbook Phase 4 with executable, append-safe rollout/abort/rollback automation; fix descriptor path and year handling.
5. Retain auditable legacy+port captures, diffs, artifact digests, and semantic verdict evidence.
6. Add a populated adopted-schema regression and fresh-DDL catalog assertion.
7. Bound/measure the per-tenant definition cache and add the route-override CI gate.

## Repository integrity and evidence

Origin worktree remained unchanged throughout the review. Review resources were cleaned; QA evidence remains at /tmp/msi-quinn-r2.yOUVFO, SRE evidence at /tmp/mod-si-rowan2.2KSUai, and the fresh semantic cache at /tmp/mod-si-party-review2.dJMrQd/semantic-repo/.sdd/semantic/verdicts.

The report is stored outside the repository because the mission explicitly required a read-only worktree.
