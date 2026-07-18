# BMAD Party Review №4 — final validation after M8

**Review date:** 2026-07-23  
**Reviewed application range:** `950764a..63c8b2f`  
**Review checkout:** `097751388b48373a3e221e5660d13a24871a62b8`  
**Decision:** **NO-GO for PR merge and production cutover**

## Executive verdict

M8 materially repairs review №3. The independent team rebuilt HEAD, passed
3 unit and 95 integration tests, built and booted the Java 21 image, ran the
unmodified 35-probe harness against independently booted legacy and port
artifacts, recomputed the retained evidence, re-judged five semantic findings
live, and drove the rewritten rollout through real Okapi.

The rollout complex now works. A two-tenant populated control wave changed and
verified routing before recording `complete`; a port-loss smoke failure caused
authenticated rollback and inverse routing verification; retry used a fresh
run directory and left all predecessor evidence byte-identical; a sabotaged
rollback hard-stopped regardless of `MAX_FAILURES`; and manual rollback plus
re-cutover succeeded.

The whole migration is not ready. Fresh attacks found **9 Major findings**:

1. the explicit-purge guard accepts schema-invalid truthy scalars and
   last-key-wins duplicate `purge` members, which can drop a tenant schema;
2. escaped logical tokens around an empty RHS bypass the R22 absorption fix
   and turn a legacy one-row result into an unfiltered port result;
3. `%` wildcard behavior differs in both contains filters and text matching,
   contradicting REQ-022 and D-1..D-32;
4. the hardened evidence proxy persists query credentials and
   `Proxy-Authorization` in cleartext;
5. TRC-023 still overstates the adoption IT from six table-count assertions
   to “per-table” ground-truth coverage;
6. the README-linked Kubernetes template routes port 8080 while the release
   image listens only on 8081;
7. `/admin/loggers` permits unauthenticated runtime log-level mutation;
8. the direct module port can turn an unverified inbound `user_id` claim into
   a valid module-signed RS256 assertion unless deployment isolation holds;
9. the M8 honesty annex omits recurring rollout and disable-cache regression
   coverage gaps.

The narrow original reproductions for F-26 and omitted/null F-32 now pass.
That does not make the broader parser-completeness and explicit-destructive-
intent claims true. F-21 remains regressed, F-32 is only partially closed, and
three of the five review №3 merge gates are only partially met.

## Review room and independent method

The review ran in Party Mode with independent ownership:

| Reviewer | Primary ownership |
|---|---|
| John, Product Manager | closure definitions, residual honesty, terminal decision |
| Winston, Architect/Security | parser oracle, tenant purge mechanism, attestation and management surface |
| Murat, QA/Test Architect/SRE | clean release, real-Okapi rollout, failure injection, runbook and scale |
| Mary, Analyst/Tech Writer | evidence forensics, semantic live sample, traceability and terminal arc |

Every mutation and runtime experiment used isolated copies and uniquely named
scratch resources outside the repository. The original checkout stayed clean
throughout the review. This report is the only repository write.

## Independent evidence summary

| ID | Reproduction | Independent result |
|---|---|---|
| E1 — clean build | `mvn -B verify` in an isolated clone | `BUILD SUCCESS`; 3 unit + 95 integration tests; 0 failures/errors |
| E2 — release image | `docker build --no-cache -t review4-murat:0977513 .` and independent architecture build | Java 21 images `108ca0…595eae` and `724047…cf75f`; `/admin/health` 200 |
| E3 — descriptor/SBOM | rebuilt `target/ModuleDescriptor.json`; validator; local Syft CycloneDX | descriptor sha256 `6c14c195…c5233dd`, byte-identical to pin; validator success; CycloneDX generated |
| E4 — unmodified harness | independently populated legacy, adopted with rebuilt port, then `capture.sh` both sides and `diff-runs.sh` | 35/35 each; 14 byte-equal, 9 sorted-equal, 12 allowed, 0 unregistered |
| E5 — rollout control | real Okapi 7.0.6, two populated tenants | both install 200, port routing listed, catalog unchanged, `EXECUTED=0 MARK_RAN=14`, 2/2 smoke through Okapi, `complete PASS` |
| E6 — different failure | pause rebuilt port after routing verification | smoke timed out; FAIL recorded; authenticated legacy rollback and inverse routing re-verification passed |
| E7 — immutable resume | hash failed run, resume in fresh run id, hash predecessor again | 22/22 files byte-identical; ledger append-only; exact run-dir reuse refused exit 2 |
| E8 — rollback sabotage | pause Okapi before rollback with `MAX_FAILURES=9` | rollback timeout produced HARD STOP; manual Phase 6 rollback, legacy smoke, and re-cutover all passed |
| E9 — response-loss reconciliation | proxy delivered install to Okapi, discarded first response | curl 52; script reconciled via installed-module state and completed safely |
| E10 — lifecycle guard | rebuilt image plus fresh schemas; omitted/null and adversarial purge bodies | omission/null safely 400; strings/numbers/duplicates expose F-37 |
| E11 — parser oracle | independently booted legacy jar and rebuilt port with discriminating rows | governed 35 rows pass; escaped-logical and `%` attacks expose F-38/F-39 |
| E12 — evidence forensics | body/hash loops, descriptor hashes, proxy artifacts, rollout sums | 189/189 captured bodies, 60/60 rollout files, semantic/proxy/descriptor hashes all match |
| E13 — semantic re-judge | hold five seeded random cache entries aside, rerun semantic lane | 165 files, 0/0, 245 PASS; 240 cache + 5 model; all five live verdict/confidence/rationale triples exact |
| E14 — tenant drift mutations | undeclared served route, declared-unserved route, status change | all three targeted builds failed; a request-schema mutation passed, matching the documented narrow scope |
| E15 — management/release | rebuilt container ports and actuator requests | 8081 health works, 8080 refuses; unauthenticated loggers GET 200 and level-change POST 204 |

### Key command excerpts

Clean build:

```text
mvn -B verify
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Tests run: 95, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Fresh harness:

```text
capture legacy: probe_count=35 failed_probes=[]
capture port:   probe_count=35 failed_probes=[]
diff-runs: byte-equal=14 sorted-equal=9 allowed=12 diverged=0
diff-runs: OK — no unregistered divergence
```

Real-Okapi failure and recovery:

```text
qafbad: Okapi install -> 200
qafbad: routing verified
curl: (28) Operation timed out after 3002 milliseconds
tenant qafbad at phase 'smoke': FAIL
rollback: Okapi enable mod-service-interaction-4.4.0-SNAPSHOT
rollback: routing re-verified

resume run: qafbad complete PASS
failed predecessor: 22/22 files byte-identical after resume
```

Rollback sabotage:

```text
automatic Okapi rollback FAILED ... HARD STOP
STOP — hard-stop failure ... aborts immediately regardless of MAX_FAILURES
manual legacy install -> 200
legacy routing listed; port absent; legacy smoke -> 200
re-cutover -> complete PASS
```

Semantic live sample:

```text
files_checked=165 total_errors=0 total_warnings=0 pass=true
gate_status=PASS findings=245
verdict_sources: cache=240 model=5
verdicts: PASS=245
```

Live model finding IDs, all exact against cache:

```text
3419f427e3b80b49ae7af937b09950736179253faa38b6fc81199eb1aa6de9e2
97dbaccd280f73500fdeec29051ad58f286eb2b91026b974bdda6588cd294b11
7549838e646548079e56824dabc1600c451321be6b1a7be1de1a17e4ed505706
ae6bc9a3505207bd4c218eea44265debe0991438760df0190e45a7a67508715e
b08ebd520d88c86b200dd40a3a88d9817fe790c47aefb62cc77869d08d1ae4c2
```

## Closure matrix

Statuses use the strict review rule: the original reproduction was rerun and
the claimed pin was inspected and executed. Fresh defects can prevent broad
closure even where the narrow original rows now pass.

| Item | Verdict | Independent reproduction |
|---|---|---|
| F-21 parser/error completeness | **REGRESSED** | The original rows and R22 matrix pass, but E11 found two unregistered parser/wire deviations; F-38 can return the full unfiltered collection. |
| F-26 compound empty-RHS | **CLOSED narrowly** | Own legacy oracle plus 7 R22 harness rows, `KiwtListingGrammarIT` 15/15, and 35/35 both sides pass. F-38 is a fresh escaped-boundary failure and keeps merge gate 2 partial. |
| F-27 rollout PASS without cutover | **CLOSED** | E5 proves install, routing and smoke through real Okapi precede `complete`. |
| F-28 retry destroys evidence | **CLOSED** | E7: immutable run directories, reuse refusal, hash-bearing ledger, predecessor 22/22 byte-identical. |
| F-29 fail-unsafe rollout | **CLOSED** | Malformed config rejection, timeouts, delivered-response-loss reconciliation, rollback failure HARD STOP, and hostile CSV all exercised. The both-install-and-reconcile-unreachable branch remains unprobed. |
| F-30 tenant normalization | **CLOSED** | Internal whitespace rejected; 40-char tenant rejected; 39-char boundary and outer trimming pass. |
| F-31 timer false abort | **CLOSED for the original defect** | Timer is registered; manual Okapi POST returns the newly documented EXPECTED-404; direct POST returns 200. A real 24-hour scheduler fire was not observed. |
| F-32 omitted purge destructive default | **PARTIALLY CLOSED** | Omitted, `{}`, and null shapes are 400/no-side-effect, and explicit false/true controls work. F-37 proves schema-invalid truthy and duplicate members can still destroy. |
| F-33 tenant API drift gate | **CLOSED for its stated scope** | Served/declared/status mutations fail. Request/response schemas, parameters, content types and headers are deliberately outside the gate; README now says so. |
| F-34 proxy credential/evidence safety | **PARTIALLY CLOSED** | Original Authorization/X-Okapi-Token/Cookie redaction, forwarding, atomic tenant files, preflight and late-sink behavior work. F-40 exposes two additional credential channels. |
| F-35 rollback command | **CLOSED** | Automatic and manual authenticated commands ran; evidence contains `$OKAPI_TOKEN`, not its value; routing and smoke were verified. |
| F-36 cache metrics | **CLOSED** | `WidgetCacheCapacityIT` 4/4 and rebuilt-image tagged metrics endpoints return 200. F-43 is the orthogonal writable-loggers exposure. |
| F-12 runbook residue | **PARTIALLY CLOSED** | Phases 4/5/6 were walked successfully. No full backup/restore drill, production-scale workload, or 24-hour scheduler observation ran this round. |
| F-13 trace/spec honesty residue | **PARTIALLY CLOSED** | Both named ITs pass 12/12, but F-41 shows TRC-023 still overstates their assertion breadth. |
| F-16 high-risk coverage residue | **PARTIALLY CLOSED** | M8 adds useful pins, but real-Okapi failure/retry remains one-off evidence, disable-cache eviction has no behavioral test, and the fresh parser/purge boundaries were absent. |
| F-22 provenance/corpus residue | **CLOSED** | Current body, semantic, rollout, proxy and descriptor hashes reproduce; five cached semantic verdicts re-judge exactly. Historical absent binaries remain identity pins rather than reconstructable artifacts, now stated honestly. |
| F-24 rollout/ledger residue | **CLOSED** | Syntax, dry-run validation, real cutover, direct labeling, old-ledger append, hostile CSV, immutable evidence, rollback, hard stop and resume all reran. |

## Review №3 merge gates

| # | Gate | Verdict | What this team ran |
|---:|---|---|---|
| 1 | F-27/28/29/30/31/35 plus real Okapi, failure, rollback, resume and routing | **MET** | E5–E9; different port-loss failure and rollback sabotage, not the implementer’s catalog-gate class |
| 2 | F-26/D-32 with compound/dotted/unknown cases pinned against legacy | **PARTIALLY MET** | Original and R22 rows pass, but the mandatory escaped-logical attack exposes F-38 and invalidates “replicates exactly.” |
| 3 | F-32 explicit-destructive-intent contract | **PARTIALLY MET** | Omitted/null safe; F-37 accepts non-Boolean and contradictory duplicate discriminators. |
| 4 | Tenant API under explicit drift/compatibility gate | **MET within documented scope** | Three own mutations fail; request/schema/header drift remains explicitly out of scope. |
| 5 | Evidence/TRC corrected, exact semantic evidence, provenance without secrets | **PARTIALLY MET** | Hashes and live semantic sample pass; F-40 proxy leak and F-41 TRC overclaim remain. |

## Terminal arc

### Nine review №1 reconsideration conditions

| # | Condition | Terminal verdict | Evidence pointer |
|---:|---|---|---|
| 1 | Java 21 release path, descriptors, SBOM | **PARTIALLY MET** | E1–E3 pass; F-42 makes the advertised Kubernetes path unroutable; hosted SBOM publication not run. |
| 2 | Published lifecycle/seeding through real Okapi | **MET** | E5–E9 and explicit lifecycle wire probes. |
| 3 | 64-bit safety, atomic first use, regressions | **MET** | Full suite, harness and retained concurrency pins pass. |
| 4 | Full listing/error contract governed with consumer evidence | **NOT MET** | F-38/F-39 are unregistered behavior differences; REQ-022 contains a false `%` claim. |
| 5 | Tenant cache/key lifecycle/display identity | **MET with residual** | Targeted cache/key tests pass; multi-replica key creation and the direct-port trust boundary remain explicit risks. |
| 6 | Fresh DDL parity and honest adoption verification | **PARTIALLY MET** | Named ITs pass; F-41 overstates table-count/catalog/readback coverage. |
| 7 | Executable canary/rollout/backup/abort/resume/timer/rollback | **PARTIALLY MET** | Rollout/rollback machinery is met; full backup/restore, production scale and 24-hour timer fire were not run. |
| 8 | Semantic proof and honest traceability | **PARTIALLY MET** | 245 PASS and five live exact re-judgments; F-41 violates honesty. |
| 9 | Empirical matrix against built release artifact | **MET** | Rebuilt image, fresh harness, lifecycle and real-Okapi waves. |

### Seven review №2 must-fix items

| # | Requirement | Terminal verdict | Evidence pointer |
|---:|---|---|---|
| 1 | Remove nested transaction starvation and cover pool size | **MET** | Full suite and established concurrency pin pass. |
| 2 | Correct/test real Okapi disable and governance | **MET** | Real inverse transitions plus disable/lifecycle pins. F-37 is the destructive complement. |
| 3 | Correct D-2; register parser deviations; rerun harness | **NOT MET** | Harness passes its governed rows, but F-38/F-39 are absent from D-1..D-32. |
| 4 | Executable append-safe rollout/abort/rollback | **MET** | E5–E9 plus direct/legacy-ledger/CSV experiments. |
| 5 | Auditable captures, diffs, digests, semantic verdicts | **PARTIALLY MET** | Existing evidence is reproducible and clean; reusable proxy remains unsafe under F-40. |
| 6 | Populated-adoption and fresh-DDL regression tests | **MET for the named pins** | 12/12 pass; do not inherit TRC-023’s broader 37-table wording. |
| 7 | Bound/measure cache and enforce drift gate | **MET** | Capacity/HTTP metrics and biting tenant-operation/status gate pass. |

### Five review №3 merge-gate items

| # | Terminal verdict | One-line grounding |
|---:|---|---|
| 1 | **MET** | Real control wave, different live failure, rollback, resume, immutable evidence and routing all independently ran. |
| 2 | **PARTIALLY MET** | Measured D-32 corpus passes; escaped logical boundary does not. |
| 3 | **PARTIALLY MET** | Omitted/null is safe; strict Boolean/duplicate destructive intent is not enforced. |
| 4 | **MET within documented scope** | Route/method/status drift mutations bite; schema/header limits are honestly named. |
| 5 | **PARTIALLY MET** | Hashes and live semantics pass; proxy and TRC honesty defects remain. |

## New findings register

### F-37 — Major — explicit purge accepts non-Boolean and duplicate discriminators

**Observed.** `TenantPurgeFlagAdvice` treats any non-null top-level `purge`
member as explicit. Jackson then coerces strings/numbers to Boolean and accepts
duplicate keys last-one-wins. The OpenAPI DTO requires a Boolean, while
REQ-020 AC6 and ADR-012 say destruction requires explicit `purge: true`.

**Reproduction.**

```text
POST /_/tenant {"module_from":"...","purge":"true"} -> 204; schema t -> f
POST /_/tenant {"module_from":"...","purge":1}      -> 204; schema t -> f
POST /_/tenant {"purge":false,"purge":true}         -> 204; schema t -> f
POST /_/tenant {"purge":true,"purge":false}         -> 204; schema t -> t

controls:
"purge":"false" -> 204, schema stays
"purge":0       -> 204, schema stays
nested purge    -> 400 purge.not.explicit, schema stays
malformed JSON  -> 400 malformed.json, schema stays
```

**Recommended action.** Inspect raw JSON before binding; require exactly one
top-level Boolean `purge` node; reject scalar coercions and duplicate `purge`
members with 400 and prove zero side effects in wire ITs.

### F-38 — Major / D-33 candidate — escaped logical tokens bypass R22 absorption

**Observed.** A literal escaped `\&&` or `\||` before a trailing empty
comparison is tokenized without a structural AND/OR. R22 absorption therefore
does not run, normal parsing fails, and the port drops the filter.

**Reproduction.**

```text
filters=code==alpha\&&prefix==
legacy 200 -> ["alpha\\&&prefix=="]
port   200 -> all 14 generator rows

filters=code==alpha\||prefix==
legacy 200 -> ["alpha\\||prefix=="]
port   200 -> all 14 generator rows

control code==alpha&&prefix==:
legacy and port -> ["alpha&&prefix=="]
```

D-29 describes simple backslash unescaping; D-32 claims exact absorption
replication. Neither records the unfiltered-data consequence, and no IT or
harness row pins it.

**Recommended action.** Re-derive an escaped-operator matrix against booted
legacy; make absorption raw-position-aware; add discriminating rows to the IT
and frozen harness; register a deviation only if the unfiltered behavior is
deliberately accepted.

### F-39 — Major / D-34 candidate — `%` wildcard semantics are unregistered and REQ-022 is false

**Observed.** With fixture codes `ab_cd`, `abXcd`, and `ab%cd`, the legacy
contains filter and text-match paths treat `%` differently from the port.

**Reproduction.**

```text
filters=code=~ab%cd
legacy 200 -> []
port   200 -> ["ab%cd"]

match=code&term=ab%cd
legacy 200 -> ["ab_cd","abXcd","ab%cd"]
port   200 -> ["ab%cd"]

control filters=code==ab%cd:
both -> ["ab%cd"]
```

REQ-022 AC1 says user `%` is literal “as legacy”; AC5 claims legacy matching
semantics. D-1..D-32 contains no `%` disposition.

**Recommended action.** Reproduce `%` and `_` across `=~`, `!~`, `match`,
quoted and multi-term cases; choose parity or an explicit deviation; correct
REQ-022 and add IT/harness rows.

### F-40 — Major — proxy retains query and proxy-authorization credentials

**Observed.** The hardened proxy correctly hashes case variants and repeated
Authorization, X-Okapi-Token and Cookie headers while forwarding originals.
It still stores `Proxy-Authorization` and credential-shaped query parameters
verbatim.

**Reproduction.**

```text
GET /probe?access_token=review4-query-secret&jwt=review4-jwt-secret
Authorization: [REDACTED sha256:...]
X-Okapi-Token: [REDACTED sha256:...]
Cookie: [REDACTED sha256:...]
Proxy-Authorization: Basic review4-proxy-secret
persisted path contains both query secrets verbatim
```

The committed evidence contains no such cleartext, so this is a reusable-tool
defect, not evidence corruption.

**Recommended action.** Redact `Proxy-Authorization` and credential-shaped
query parameters before persistence, retaining only a correlation hash; add
multi-value/case/query regression tests.

### F-41 — Major — TRC-023 still overstates adoption evidence

**Observed.** TRC-023 says `AdoptedSchemaUpgradeIT` proves “per-table row
counts identical to the r13b ground truth.” The ground-truth file has 37
tables; the IT checks counts for six. Its catalog assertion compares table
names only, and wire readback checks one generator code plus a 200 status for
widget definitions.

**Reproduction.**

```text
mvn -B -Dit.test=AdoptedSchemaUpgradeIT,FreshDdlCatalogIT verify
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

run2/rowcounts.tsv: 37 tables
EXPECTED_LEGACY_ROWS: 6 tables
```

The stated “bit-for-bit row-content” residual is true but incomplete:
31 table row counts, most values, and most catalog objects are also unasserted.

**Recommended action.** Scope TRC-023 literally to the table-name census,
six-table count sample, one generator readback and targeted DDL properties; or
add a complete 37-table/content/catalog pin.

### F-42 — Major — advertised Kubernetes template routes the wrong port

**Observed.** `README.md` links `scripts/k8s_deployment_template.yaml`, which
declares `containerPort: 8080` and Service `port: 8080` without `targetPort` or
a server-port override. The application, Dockerfile, descriptor and CI use
8081.

**Reproduction.**

```text
rebuilt image ExposedPorts -> {"8081/tcp":{}}
inside container GET 127.0.0.1:8081/admin/health -> success
inside container GET 127.0.0.1:8080/admin/health -> connection refused
```

**Recommended action.** Change the template to 8081 with explicit
`targetPort: 8081`, or configure one consistent server port; add a rendered
manifest/service smoke test.

### F-43 — Major — writable loggers endpoint is unauthenticated

**Observed.** The rebuilt image exposes `/admin/loggers` on the same module
port. No token or tenant is required to mutate runtime logging, and the
repository contains no NetworkPolicy, management authentication, separate
listener, or documented enforcement.

**Reproduction.**

```text
GET  /admin/loggers                         -> 200
POST /admin/loggers/org.folio.servint
     {"configuredLevel":"TRACE"}            -> 204
GET  /admin/loggers/org.folio.servint       -> configuredLevel TRACE
POST reset configuredLevel:null             -> 204
```

Spring Boot documents that loggers are writable and recommends securing
exposed actuator endpoints or placing them behind an enforced firewall:
[endpoint security guidance](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html),
[loggers endpoint](https://docs.spring.io/spring-boot/reference/actuator/loggers.html).

**Recommended action.** Expose only `health,metrics` by default, or move
management to an authenticated and policy-isolated listener. If loggers must
remain, commit or reference the enforceable deployment control and document
the trust boundary.

### F-44 — Major — direct-port trust can launder an unverified user claim into a signed assertion

**Observed.** When `X-Okapi-User-Id` is absent, `AttestationController` parses
`X-Okapi-Token` without signature verification. An invalid-signature HS256
token with a chosen `user_id` produced a valid module-signed RS256 assertion
for that subject and supplied tenant.

**Reproduction.**

```text
GET /servint/attestation/token
X-Okapi-Tenant: w4parse
X-Okapi-Token: <HS256-shaped token with invalid signature and chosen user_id>
-> 200

returned header: alg=RS256, kid=<tenant key row UUID>
returned claims: sub=<chosen user_id>, tenant=w4parse, exp-iat=300
```

The endpoint is permission-protected through Okapi, so exploitability depends
on Okapi header replacement and direct module-port isolation. The checked-in
ClusterIP template supplies neither a NetworkPolicy nor application
authentication, making that trust assumption unenforced in this repository.

**Recommended action.** Prefer trusted `X-Okapi-User-Id`; verify fallback
token signature/algorithm/issuer/tenant/expiry against a trusted key, or
enforce and document an Okapi-only module-port boundary.

### F-45 — Major — M8 residual list omits recurring safety coverage gaps

**Observed.** M8 honestly states that only one failure class ran live, but it
does not state that the real-Okapi failure/retry/rollback path is retained as
one-off evidence rather than an automated regression lane. It also omits that
no behavioral test warms both tenant caches, performs disable, and proves
both evictions. `TenantEnableIT` checks database side effects only.

**Recommended action.** Keep F-16 PARTIALLY CLOSED; list both limitations in
the completion residuals; add an automated rollout integration lane and a
disable-cache eviction test.

## Residual honesty audit

| Residual | Assessment |
|---|---|
| Bit-for-bit row identity unasserted | True but understated: F-41 adds missing 31-table count, value and catalog scope. |
| One live rollout failure class | True. This review adds port loss, rollback loss and response loss, but they remain review evidence rather than CI. |
| Cert semantic corpus cache-replayed | True; E13 live-revalidated five deterministic samples exactly, not all 245. |
| Multi-replica signing-key first creation | Still unprobed and correctly carried from M7. |
| Production-scale load | Still unprobed; 60-call burst and 600-cache IT are not production soak. |
| Grammar fuzzing beyond matrix | Material: F-38/F-39 prove the residual is risk-bearing. |
| Direct module-port trust | Previously listed as unprobed by review №3, omitted from M8 annex; F-43/F-44 show why it matters. |
| Disable-cache behavioral eviction | Omitted; F-45. |
| Hosted SBOM publication/registry release | Not exercised; local CycloneDX generation passed. |

## Coverage map

| Area | Verified | Explicitly unprobed or limited |
|---|---|---|
| Build/release | Clean 3+95, two rebuilt images, Java 21 boot, deterministic descriptor, validator, local SBOM | Hosted CI/upload, registry publication, production memory limit |
| Wire parity | Fresh unmodified 35×2, governed diff 0; exploratory escaped/%/purge/header/content attacks | Exhaustive grammar/property fuzzing and all consumer inputs |
| Rollout | Control wave, port-loss rollback, rollback sabotage, response-loss reconciliation, resume, immutable evidence, direct mode, old ledger, hostile CSV | Partition where install and reconciliation both fail; non-7.0.6 Okapi |
| Runbook/data | Populated adoption, catalog/count no-shrink, rollback/re-cutover, timer registration/semantics | Full backup/restore this round; 24-hour timer fire; bitwise 37-table row identity |
| Tenant lifecycle | Omitted/null/false/true plus type, duplicate, malformed, nested and chunked attacks | Concurrent lifecycle race and non-UTF-8 body variants |
| Security | Fixed RS256 output, UUID kid, 300-second expiry, tenant-keyed cache, Criteria injection attempts, management and direct-port attacks | Production NetworkPolicy/Okapi header behavior; multi-replica key creation; broad fuzzing |
| Cache/scale | 600-tenant capacity/eviction IT, HTTP metrics, 60 calls at concurrency 20 | Real expiry race, multi-hundred simultaneous enables, sustained production soak |
| Evidence | 189 body hashes, 60 rollout sums, semantic/proxy/descriptor hashes, current credential sweep | Crash between temp write/rename; exact historical binary reconstruction |
| Semantic/spec | 165/0/0, 245 PASS, five exact live re-judgments, three drift mutations | Remaining 240 live judgments; schema/header drift gate; full TRC symbol sweep |

## Dissent preserved

1. **F-32 labeling.** The narrow position calls omission/null CLOSED and
   records F-37 separately. Security and Architecture reject full closure
   because the completion claim is an explicit Boolean destructive contract,
   not merely member presence. Final position: PARTIALLY CLOSED.
2. **Escaped filters.** D-29 says backslashes can differ, but it describes
   literal-vs-unescape behavior and claims limited impact. It does not disclose
   a one-row-to-unfiltered expansion when combined with R22. Final position:
   new Major deviation, not covered by D-29.
3. **Proxy evidence.** Every retained credential value is currently redacted
   and all hashes hold. The defect is future capture safety, not corruption of
   the committed bundle. Final position: F-34 PARTIALLY CLOSED plus F-40.
4. **TRC-023.** The adoption test is valuable and green; the finding is the
   governed description’s breadth, not observed data loss. Final position:
   Major honesty defect because cutover confidence depends on exact scope.
5. **Kubernetes template.** It can be defended as “sample only.” README
   actively directs operators to it and it deterministically creates a dead
   Service. Final position: Major and must-fix before merge.
6. **Writable loggers.** Cluster-internal module ports may be an accepted FOLIO
   assumption. Cluster reach is not authorization, and no checked-in control
   proves the assumption. Final position: Major unless deployment owners
   provide enforceable isolation.
7. **Attestation fallback.** This is legacy parity and a documented fallback,
   not an algorithm-confusion flaw in the outgoing token. It still converts an
   unverified identity into a trusted assertion if direct reach exists. Final
   position: resolve or formally accept the module-port trust boundary.
8. **F-31.** The false-abort instruction is fixed, so F-31 is CLOSED. Actual
   scheduler health remains a pre-cutover canary condition, not proof supplied
   by this review.

## Final recommendation and must-fix list

**NO-GO for PR merge and production cutover.**

The rollout implementation itself is ready. The release is not ready until:

1. F-37 enforces exactly one top-level Boolean purge discriminator and proves
   all invalid shapes side-effect-free.
2. F-38/F-39 are corrected or explicitly governed from a booted-legacy oracle,
   with REQ-022, the deviation register, IT and frozen harness aligned.
3. F-40 redacts every credential channel in the reusable evidence proxy.
4. F-41 narrows TRC-023 or adds the broader data-continuity assertions.
5. F-42 aligns the advertised Kubernetes Service/container port with 8081 and
   adds a service-level smoke.
6. F-43 removes or secures writable loggers, and F-44’s direct-port trust
   boundary is enforced or explicitly accepted by Security/Operations.
7. F-45’s recurring rollout and disable-cache regression gaps are recorded and
   assigned concrete automation owners.

After remediation, rerun the targeted purge matrix, escaped/% legacy oracle,
35-probe harness, proxy credential suite, data-continuity pins, rendered
Kubernetes service smoke, management-security probe, semantic live sample,
and one real-Okapi failure/rollback/resume wave. The evidence does not require
a fifth wholesale re-review if those exact gates are independently reproduced
and no new application behavior is introduced.
