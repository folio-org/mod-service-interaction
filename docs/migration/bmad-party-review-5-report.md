# BMAD Party Review №5 — exhaustive final validation after M9

**Review date:** 2026-07-23

**Migration candidate:** `b14843c4d51f3a1a0bc1a4eda62c44385d397b61`

**Review HEAD:** `641afb13844ba91634b91850e02fb2b96db6f836` (adds only the review-5 mission brief after the candidate)

**Legacy oracle:** `service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar`, SHA-256 `abb3721190394d2eea3e5661345b4d0fd8da89add558e0fc9924b26ec79b4c57`
**Rebuilt review image:** `mod-service-interaction:review5-head`, image ID `sha256:e7d37f629fe0…a8ce1`

## Executive verdict

**NO-GO for PR merge and production cutover.**

M9 did real work. The strict purge discriminator, bug-for-bug wildcard
transform, port alignment, default management exposure, adopted-schema
37-table count comparison, cache-eviction test, three-leg harness, semantic
gate, and real-Okapi rollback/resume lane all reproduced successfully.
This review does not retract those results.

The exhaustive sweep nevertheless found **seven new Major findings and one
Minor finding**:

- two unregistered wire deviations in the twice-regressed parser surface
  (**F-46/D-35** terminal backslash and **F-47/D-36** NUL handling);
- a broader credential-redaction bypass despite the green 33/33 proxy suite
  (**F-48**);
- a NetworkPolicy test that remains green after effective ingress is widened
  (**F-49**);
- a rebuilt production image running an old JRE from a mutable base
  (**F-50**);
- an architecture graph that omits ADR-013 (**F-51**, Minor);
- another TRC-023 assurance overclaim (**F-52**); and
- a recurring rollout lane that passes a non-HEAD JAR (**F-53**).

The review-4 findings end as **4 CLOSED and 5 PARTIALLY CLOSED**. None is
OPEN or REGRESSED under the standing closure definitions, but the new
findings prevent release.

## Review room and method

Party Mode ran with independent reviewer contexts:

| Reviewer | Primary ownership |
|---|---|
| 📋 John, Product Manager | scope, terminal arc, minimal-diff audit, decision integrity, synthesis |
| 💻 Amelia, Dev + QA/Test Architect | build, rebuilt runtime, purge/parser/wildcard oracles, harness, proxy, rollout |
| 🏛️ Winston, Architect + Security/SRE | Kubernetes, management, attestation boundary, release/runtime posture |
| 📊 Mary, Analyst + Tech Writer | evidence forensics, continuity, residual honesty, specs, semantic governance |

The repository was read-only throughout the review until this report was
written. Builds, mutations, captures, semantic cache eviction, and rollout
experiments ran in disposable copies or runtime rigs outside the repository.
Implementer documents were treated as claims. The worktree was clean before
this report was added.

The M9 range contains 14 commits:

```text
git rev-list --count 000b8a6^..b14843c
14

git diff --quiet 000b8a6^..b14843c -- service
exit 0
```

The only M9 commits touching `src/main` are:

```text
4316158  KiwtFilterParser + KiwtListing
9faa90b  purge advice + exception path
644a828  management exposure
```

## Independent evidence summary

| Evidence | Independent result |
|---|---|
| Clean build | `mvn -B clean verify` → **7 unit + 106 integration tests**, 0 failures, `BUILD SUCCESS` |
| Release artifact | Rebuilt image booted on 8081; descriptor SHA-256 `6c14c195…5233dd`, byte-identical to the prior pin |
| F-37 purge matrix | Coercions, duplicates, array, exponential/decimal numbers and unicode-escaped key rejected 400; controls and zero-side-effect probes passed |
| F-38/F-39 oracle | Fresh 41-probe legacy/port run: 39 semantic equals, two registered differences (D-33/D-34); planted `ab$2cd` proof passed both sides |
| Harness | Three fresh 45-probe legs, every `failed_probes=[]`; legacy→adopted-port diff exit 0: 23 byte-equal, 7 sorted-equal, 15 allowed, 0 diverged |
| Rollout | Unmodified `rollout.sh` (`2fc32b1f…ace679`) drove control, forced catalog failure, verified Okapi rollback, repair, resume, immutability and final routing; all ten gates passed |
| Continuity/cache | `AdoptedSchemaUpgradeIT` 8/8, `FreshDdlCatalogIT` 4/4, `TenantDisableCacheEvictionIT` 3/3 |
| Management | Default discovery exposed exactly health+metrics; logger GET/POST 404; documented override restored logger GET/POST and TRACE mutation |
| Attestation | Direct-port invalid-signature/expired/wrong-tenant input still minted a valid RS256 assertion for an arbitrary subject, as DP-3(a) accepts |
| Kubernetes | Baseline 4/4 and strict render 3/3 valid; required port/policy/probe mutations failed; security-widening mutations passed unexpectedly (F-49) |
| Semantic | Full cached gate 166 files / PASS 249; independent eviction of five known M9 entries produced 244 cache + 5 live model verdicts, all PASS at 0.95 |
| Evidence hashes | 132/132 R-VER harness bodies, all rollout `sha256sums.txt` manifests, and all six semantic corpus hashes recomputed |
| Proxy | Shipped self-test 33/33; separate hostile tap exposed F-48 |
| Minimal diff | Every `src/main` hunk traced to F-37, F-38, F-39, F-43 or DP-1/DP-2; legacy `service/` unchanged |

## Closure matrix — review №4 findings F-37..F-45

| Finding | Verdict | Reproduction and basis |
|---|---|---|
| F-37 — explicit purge accepts coercible/duplicate discriminators | **CLOSED** | Rebuilt-image wire matrix rejected strings, integers, `1e0`, `0.0`, arrays, `module_to` coercion and duplicates both orders with 400 `purge.not.explicit`; nested/malformed controls kept their paths; schema probes proved no side effects; explicit false/true retained/dropped as intended. |
| F-38 — escaped-token absorption / F-21 lineage | **PARTIALLY CLOSED** | Original escaped-token reproduction and the committed R31 matrix now match booted legacy; orders 16–17 run. F-46 exposes the same new tokenizer at an unhandled terminal-backslash boundary, so the mechanism is not fully closed. |
| F-39 — `%`/`_` wildcard semantics | **CLOSED** | Both booted sides matched planted `ab$2cd` for `=~` and unwrapped `=i=`; `$1`/`$2`, multiple/consecutive/leading `%`, `\%%`, `!~`, `_`, and match probes agreed. DP-1(a) fidelity is demonstrated for the directed matrix. |
| F-40 — proxy credential channels | **PARTIALLY CLOSED** | The original query and `*-authorization` reproductions are fixed and self-test 33/33 passes. F-48 remains the same credential-retention failure class for common novel headers and encoded credential parameter names. |
| F-41 — 37-table continuity / TRC-023 honesty | **PARTIALLY CLOSED** | AdoptedSchemaUpgradeIT genuinely parses and compares all 37 fixture row counts before/after upgrade. TRC-023 still claims a fresh-schema “table catalog” pin that neither TenantEnableIT nor FreshDdlCatalogIT implements (F-52). |
| F-42 — Kubernetes port routing | **CLOSED** | Current Deployment, Service, probes and policy use 8081; baseline test and strict render pass; containerPort, targetPort, missing-policy, second-container and probe-path mutations fail. F-49 is a separate security-effectiveness defect, not recurrence of the dead-Service bug. |
| F-43 — unauthenticated writable loggers | **CLOSED** | Rebuilt default image returns 404 for logger GET/POST while health, metrics and cache meters remain 200. `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,loggers` was proven to re-enable the documented unauthenticated mutation and is framed honestly. |
| F-44 — direct-port attestation trust boundary | **PARTIALLY CLOSED** | ADR-013, README warning and current restrictive NetworkPolicy are coherent. The direct-port attack still works by accepted design. F-49 proves the claimed drift pin does not prevent effective ingress widening, and production cluster enforcement was not demonstrated. |
| F-45 — residual honesty / cache / recurring rollout | **PARTIALLY CLOSED** | Cache eviction is behaviorally non-vacuous; the real rollout lane ran successfully. The corrected residual list still omits F-49/F-50/F-52/F-53 implications, and the recurring lane cannot attest artifact identity. |

## Review №4 waiver gates plus minimal-diff condition

| # | Gate | Verdict | What this team ran |
|---:|---|---|---|
| 1 | Targeted purge matrix over the wire | **MET** | Fresh rebuilt-image tenant; original coercions/duplicates plus numeric, unicode-key, malformed/nested, false/true, UTF-16 and side-effect controls. |
| 2 | Escaped/`%` legacy oracle, both sides | **PARTIALLY MET** | Original 41-probe matrix and planted wildcard proof pass; F-46/F-47 show adjacent tokenizer/encoding boundaries remain ungoverned. |
| 3 | Full harness, both sides + adoption | **MET** | Three fresh 45-row manifests, zero failed probes; unmodified diff exit 0 with 0 divergences. |
| 4 | Proxy credential suite | **PARTIALLY MET** | Named 33/33 suite passes, but the required hostile extension leaks common credential headers and an encoded credential parameter name (F-48). |
| 5 | Data-continuity pins | **PARTIALLY MET** | Adopted 37-table pin and named tests pass; fresh-schema table-catalog assurance remains false (F-52). |
| 6 | Rendered Kubernetes service smoke | **PARTIALLY MET** | Port/probe/schema checks pass and bite, but security-widening policies/Ingress remain green (F-49). |
| 7 | Management-security probe + boundary docs | **MET** | Default logger flip 404, health/metrics 200, exact discovery checked, override proven, boundary docs walked. |
| 8 | Semantic full gate + live sample | **MET** | Full PASS 249/249; five known M9 cache entries independently evicted and live-rejudged PASS. F-51 remains an advisory graph defect. |
| 9 | Real-Okapi failure/rollback/resume | **MET for this execution** | The artifact was freshly built from review HEAD, then control/failure/rollback/repair/resume and final routing all ran. F-53 prevents treating every future green lane as proof of HEAD identity. |
| 10 | Minimal-diff audit | **MET** | Six `src/main` files, three behavior commits, every hunk mapped to a named finding or confirmed DP; `service/` unchanged. |

## Terminal arc — all five reviews

`⚠` flags a terminal result that conflicts with a broad M9 closure/readiness
claim.

### Nine review №1 reconsideration conditions

| # | Condition | Terminal verdict | Independent grounding | Claim conflict |
|---:|---|---|---|:---:|
| 1 | Java 21 release path, descriptors, SBOM | **PARTIALLY MET** | Build/descriptor/local SBOM path works; release image is on stale JRE 21.0.8 and hosted SBOM remains unproven (F-50). | ⚠ |
| 2 | Published lifecycle/seeding through real Okapi | **MET** | Fresh real-Okapi control/failure/rollback/resume lane and lifecycle pins. | |
| 3 | 64-bit safety, atomic first use, regressions | **MET** | Full suite, established concurrency pins and fresh harness green. | |
| 4 | Full listing/error contract governed with consumer evidence | **NOT MET** | F-46/D-35 and F-47/D-36 are new unregistered wire deviations. | ⚠ |
| 5 | Tenant cache/key lifecycle/display identity | **PARTIALLY MET** | Cache eviction proven; multi-replica key creation remains unprobed and deployment trust is conditional under F-49. | ⚠ |
| 6 | Fresh DDL parity and honest adoption verification | **PARTIALLY MET** | Adopted 37-table counts pass; fresh table-catalog claim is not asserted (F-52); bitwise content remains residual. | ⚠ |
| 7 | Executable canary/rollout/backup/abort/resume/timer/rollback | **PARTIALLY MET** | Real wave works; F-53 breaks recurring artifact identity; production backup/scale and 24-hour fire remain unrun. | ⚠ |
| 8 | Semantic proof and honest traceability | **PARTIALLY MET** | Semantic full gate and live sample pass; F-51/F-52 remain. | ⚠ |
| 9 | Empirical matrix against built release artifact | **MET** | Rebuilt image, three 45-probe legs, adoption diff exit 0. Exploratory F-46/F-47 sit outside the frozen matrix. | |

### Seven review №2 must-fix items

| # | Requirement | Terminal verdict | Independent grounding | Claim conflict |
|---:|---|---|---|:---:|
| 1 | Remove nested transaction starvation and cover pool size | **MET** | Full suite and retained concurrency pin pass. | |
| 2 | Correct/test real Okapi disable and governance | **MET** | Real inverse transitions, disable, purge and cache-eviction pins pass. | |
| 3 | Correct D-2; register parser deviations; rerun harness | **NOT MET** | Harness passes, but D-35/D-36 are absent from D-1..D-34. | ⚠ |
| 4 | Executable append-safe rollout/abort/rollback | **MET** | Control, failure, rollback, repair, resume and immutable failed-run evidence reproduced. | |
| 5 | Auditable captures, diffs, digests, semantic verdicts | **PARTIALLY MET** | Retained known channels and hashes are clean; reusable proxy still leaks F-48 channels. | ⚠ |
| 6 | Populated-adoption and fresh-DDL regression tests | **PARTIALLY MET** | Adopted pin is strong; fresh table catalog is not asserted despite TRC-023 wording. | ⚠ |
| 7 | Bound/measure cache and enforce drift gate | **MET** | Cache capacity/metrics and tenant surface drift pins pass in their documented scopes. | |

### Five review №3 merge-gate items

| # | Gate | Terminal verdict | Independent grounding | Claim conflict |
|---:|---|---|---|:---:|
| 1 | Real Okapi failure, rollback, resume, routing | **MET** | Fresh ten-gate lane run. | |
| 2 | Parser correction/governance | **PARTIALLY MET** | Original R31/R32 matrix passes; F-46/F-47 remain. | ⚠ |
| 3 | Explicit destructive intent | **MET** | Strict wire discriminator and side-effect controls pass. | |
| 4 | Tenant API compatibility/drift gate | **MET within documented scope** | Named mutations and runtime contract pass; schema/header limits remain explicit. | |
| 5 | Evidence/TRC/semantic provenance | **PARTIALLY MET** | Hashes/semantics pass; F-48/F-51/F-52 remain. | ⚠ |

### Ten review №4 waiver conditions

| # | Condition | Terminal verdict | Independent grounding | Claim conflict |
|---:|---|---|---|:---:|
| 1 | Purge matrix | **MET** | Fresh wire reproduction plus expanded hostile shapes. | |
| 2 | Escaped/wildcard oracle | **PARTIALLY MET** | Named matrix passes; terminal escape and NUL neighbors do not. | ⚠ |
| 3 | Full harness + adoption | **MET** | Fresh 45×3; diff exit 0. | |
| 4 | Proxy credential suite | **PARTIALLY MET** | 33/33 plus F-48 bypass. | ⚠ |
| 5 | Data-continuity pins | **PARTIALLY MET** | Adopted 37-table proof passes; F-52. | ⚠ |
| 6 | Rendered Kubernetes smoke | **PARTIALLY MET** | Routing pin works; effective security widening passes (F-49). | ⚠ |
| 7 | Management-security probe | **MET** | Default/override and docs independently reproduced. | |
| 8 | Semantic live sample | **MET with advisory** | Full gate plus five live rejudgments; F-51 warning remains. | |
| 9 | Real-Okapi failure/rollback/resume | **MET for this execution** | Fresh-built artifact and full wave independently observed; F-53 limits recurrence assurance. | |
| 10 | Minimal diff | **MET** | Hunk-by-hunk F/DP pedigree; no legacy change. | |

## New findings register

| ID | Severity | What was observed | Reproduction summary | Recommended action |
|---|---|---|---|---|
| F-46 | **Major** | Terminal backslash produces unfiltered legacy results but an exact port match; absent from D-1..D-34. | Booted legacy/port planted-row wire probe, 62 rows vs 1. | Re-oracle, fix or register D-35, add discriminating IT + harness row. |
| F-47 | **Major** | NUL filters diverge 500 vs 409, and pure NUL becomes 200-unfiltered on the port; absent from D-1..D-34. | Raw `%00` over both containers and multiple operators/match path. | Validate NUL before JPA, choose governed response, register D-36, narrow integrity mapping. |
| F-48 | **Major** | Proxy persists common credential headers and encoded credential parameter names in cleartext. | Live hostile proxy tap after 33/33 self-test. | Canonicalize names and use conservative credential-family redaction; extend self-test. |
| F-49 | **Major** | Drift test certifies a schema-valid NetworkPolicy widened to all namespaces/additive allow-all policy and an extra Ingress. | Throwaway YAML mutations; test 4/4 and kubeconform 3/3 remained green. | Pin effective source selectors and reject additive/public exposure; test rendered cluster policy and denial. |
| F-50 | **Major** | Rebuilt release image uses Temurin 21.0.8 from a mutable, year-old `latest` base. | Manifest/image inspection plus `java -version`; no APK-managed JRE. | Publish/pin patched JRE base by version+digest, scan final image, gate runtime age/version. |
| F-51 | **Minor** | ADR-013 is schema-valid but orphaned from the attestation ArchitectureComponent. | Full structural gate warning and refs inspection. | Link ADR-013 from the attestation architecture component. |
| F-52 | **Major** | TRC-023 claims a fresh-schema table-catalog pin that no named test implements. | Test method/source census and targeted suite 12/12. | Remove the claim or add an explicit expected fresh-table census. |
| F-53 | **Major** | Rollout lane passes all ten gates with a valid JAR whose hash differs from the fresh HEAD JAR. | ZIP-comment mutation changed SHA-256; `unzip -t` clean; lane exit 0. | Build inside lane or require/record source revision and expected JAR/image/descriptor digests. |

### F-46 — terminal-backslash parser divergence (D-35 candidate)

The review planted a generator code whose last character is a literal
backslash:

```text
GET ...?perPage=100&filters=code%3D%3Dtrail%5C

legacy :8080 -> HTTP 200, rows=62 (filter dropped; unfiltered)
port  :18081 -> HTTP 200, rows=1, codes=["trail\\"]
```

`KiwtFilterParser` retains a final backslash as ordinary text. Legacy treats
the incomplete escape as malformed and drops the clause. Because this is a
behavioral difference absent from D-1..D-34, it is at least Major under the
mission rules.

### F-47 — NUL handling divergence (D-36 candidate)

```text
filters=code==ab%00cd
filters=code=~ab%00cd
match=code&term=ab%00cd

legacy -> HTTP 500, "Uncaught Internal server error"
port   -> HTTP 409, integrity.violation
port log -> SQLSTATE 22021, invalid UTF-8 byte 0x00
```

The pure-NUL case is riskier:

```text
filters=code%3D~%00
legacy -> HTTP 500
port   -> HTTP 200, unfiltered full listing
```

The port trims pure NUL into empty text; embedded NUL reaches PostgreSQL and
is broadly mapped to a misleading 409 conflict. The malformed-encoding
neighborhood also showed legacy 200 vs port 400 for overlong/invalid UTF-8,
beyond the single D-34 example.

D-34's impact claim is plausible for the sampled official UI:
`ui-service-interaction` uses `generateKiwtQueryParams`, and its test expects
an encoded filter (`filters=code%3D%3Dtesting`); the underlying generator
uses `encodeURIComponent` by default. This is not an exhaustive consumer
survey. Sources:
[official UI hook](https://github.com/folio-org/ui-service-interaction/blob/master/src/public/hooks/useNumberGenerators/useNumberGenerators.js),
[query generator](https://github.com/gbv/stripes-kint-components/blob/master/src/lib/utils/generateKiwtQueryParams/generateKiwtQueryParams.js).

### F-48 — incomplete credential-channel redaction

The shipped self-test passed 33/33. A separate live tap then sent common
credential-bearing headers and an encoded credential parameter name:

```text
X-Api-Key: REVIEW5-XAPIKEY-SECRET
X-Auth-Token: REVIEW5-XAUTHTOKEN-SECRET
X-JWT: REVIEW5-XJWT-SECRET
Authentication: REVIEW5-AUTHN-SECRET
Foo-Authorization: REVIEW5-SUFFIX-SECRET

/probe?access_token=...&access%5ftoken=REVIEW5-ENCODED-NAME-SECRET
```

The first four headers and `access%5ftoken` value persisted verbatim.
`Foo-Authorization`, Cookie, and ordinary mixed-case/repeated known query
names redacted correctly. Response `Set-Cookie` was relayed but not persisted.
The retained committed JSONL files contained zero cleartext hits under the
known-header sweep; this finding is future capture safety, not proof that
the current bundle contains a credential.

### F-49 — NetworkPolicy enforcement pin can pass widened ingress

The current template is restrictive. The defect is in the claim that the
test pins that restriction.

Replacing the policy source with:

```yaml
from:
  - namespaceSelector: {}
```

left `K8sDeploymentTemplateTest` at 4/4 and kubeconform at Valid 3/3. Adding
a second policy selecting the module with `ingress: - {}` also passed, as did
an extra Ingress document. NetworkPolicies combine additively, so these
mutations can widen effective ingress while the original restrictive policy
remains present. See the
[Kubernetes NetworkPolicy semantics](https://kubernetes.io/docs/concepts/services-networking/network-policies/).

DP-3(a) is sufficient only if the deployed cluster proves CNI enforcement,
correct Okapi namespace/labels, protection against label impersonation, no
additive widening, and denial from representative non-Okapi sources.

### F-50 — stale mutable JRE base

```text
Dockerfile:
FROM folioci/alpine-jre-openjdk21:latest

remote config digest:
sha256:18d4545856f10cc2dbebdcd6f56f04b3858a3ee7178051494f604ba482056c38

local base Created:
2025-08-05

review image:
openjdk version "21.0.8" 2025-07-15 LTS
Temurin-21.0.8+9
```

The remote `latest` still resolves to the same stale config. `apk info`
contains no OpenJDK package, so the Dockerfile's `apk upgrade` cannot update
the embedded runtime. Temurin has since published 21.0.11 and 21.0.12; the
July 2026 Java CPU shipped Java 21.0.12. Sources:
[Temurin 21 releases](https://github.com/adoptium/temurin21-binaries/releases),
[Oracle July 2026 Java CPU](https://docs.oracle.com/en-us/iaas/releasenotes/java-management/jdk-cpu-july-2026.htm).

### F-51 — ADR-013 architecture graph orphan

The ADR passes single-file schema validation, but the full corpus reports:

```text
cross-ref.orphan-node:
ADR-013 has no ArchitectureComponent context
```

The attestation component references REQ-016 and ADR-009, not ADR-013.
Graph consumers can miss the decision that defines the component's security
boundary.

### F-52 — TRC-023 still overclaims fresh-schema coverage

The adopted side is materially improved: the test restores the R13 dump,
parses exactly 37 fixture counts, compares all 37 before and after upgrade,
requires all 14 adoption changesets MARK_RAN and allows only the two
Liquibase bookkeeping tables as additions.

The remaining TRC sentence says the fresh-schema tests pin targeted DDL
properties including the “table catalog.” Their complete relevant coverage
is schema existence/lifecycle plus four spot assertions: one default,
two missing PKs, two FK names and one column ordinal. Neither test queries
`information_schema.tables` or compares an expected table set.

### F-53 — rollout lane does not attest artifact identity

The fresh HEAD JAR was:

```text
2197f1411cb46674d2f672723f50c34cd74b7bb7b2dc47ecf6ef438b0bca8021
```

A behavior-equivalent valid JAR carrying a different ZIP comment was:

```text
aa19408a3b71c6d5439316e306597540ef4f32501255a58feb8a3aa449a8671a
```

`unzip -t` passed. Gate 1 accepted it, and the unmodified lane subsequently
reported all ten gates passed and exit 0. The lane proves the sampled
behavior; it does not prove the tested artifact came from closing HEAD.

## Residual honesty audit

| Residual or removed residual | Assessment |
|---|---|
| Bit-for-bit row-content identity | Accurately stated and still unprobed. |
| Production-scale load/soak | Accurately stated and still unprobed. |
| Multi-replica key creation | Accurately stated and still unprobed. |
| 24-hour timer fire | Accurately stated and still unprobed. |
| Hosted SBOM publication | Accurately stated and still unprobed. |
| Directed, not fuzzed, grammar | Accurate and materially risk-bearing: F-46/F-47. |
| Only one live rollout failure class | Accurate. |
| Direct-port trust “accepted and enforced” | Current template is restrictive, but production enforcement and a biting drift pin are not established (F-49). |
| Tenant-disable cache eviction | Correctly removed as a residual; non-vacuous 3/3 proof. |
| One-off rollout evidence | Recurring execution exists, but artifact identity is not pinned (F-53). |
| Semantic cache replay | Correctly mitigated for M9 changes; committed 19-live split is coherent and five were rejudged live. |
| Fresh-schema table catalog | Omitted and unasserted (F-52). |
| Release JRE currency/provenance | Omitted; rebuilt artifact is stale/mutable (F-50). |

One additional availability observation was not promoted to a separate
finding: a valid 20 MiB tenant body was fully read and walked before the
expected rejection, and `readAllBytes()` has no local request-size bound.
Depth 1500 failed safely as malformed JSON. Maximum safe size and memory
pressure remain unmeasured.

## Coverage map

| Area | Verified in this review | Explicitly unprobed or limited |
|---|---|---|
| Build/release | Clean 7+106, rebuilt image, Java/runtime inspection, descriptor determinism, local SBOM path | Hosted SBOM, registry promotion, vulnerability scan after base refresh |
| Wire parity | Fresh 45×3 harness, purge matrix, R31/R32 oracle, planted wildcard proof, D-34 neighborhood, terminal escape and NUL attacks | Exhaustive grammar/property fuzzing; every consumer encoding behavior |
| Rollout | Real Okapi control, forced failure, verified rollback, repair, resume, immutability, final routing; artifact-mismatch sabotage | Every gate sabotaged separately; other live failure phases; production-scale wave |
| Data continuity | 37 adopted-table counts, fresh DDL spot pins, cache eviction, repeat upgrade | Bit-for-bit row contents, fresh full table census, production backup/restore this round |
| Security | Direct-port attestation attack, ADR/README chain, default/override management, policy/render mutations, evidence credential sweep | Actual CNI/dataplane, cluster labels/admission/additive policies, consumer JWT verification |
| Semantic/spec | 166 structural, 249 cached full gate, five live M9 samples, governed-session hash coherence | Remaining 244 live rejudgments; clean-clone portability of gitignored `.sdd` session history |
| Scale/performance | Existing pool/cache tests; 20 MiB purge body observation | Sustained soak, heap exhaustion boundary, multi-replica key race, 24-hour timer fire |
| Evidence forensics | 132 harness bodies, rollout manifests, semantic hashes, probe-list evolution, retained known credentials | Exact historical binary reconstruction and externally signed fixture provenance |

## Dissent preserved

1. **F-40 labeling.** A narrow closure view says the two original omitted
   channels are fixed and records F-48 separately. Evidence/QA argue the
   same safety property remains broken. Final: **PARTIALLY CLOSED**.
2. **F-42 versus F-44.** The current template is internally consistent and
   restrictive, so F-42 is CLOSED. Architecture rejects full F-44 closure
   because F-49 defeats the enforcement pin and no production cluster was
   tested.
3. **Gate 9.** QA calls the specific run MET because its artifact was freshly
   built and its provenance was observed. Architecture/Analysis call the
   recurring gate partial because F-53 permits future false certification.
   Final: **MET for this execution**, with F-45 partial and F-53 Major.
4. **F-41 credit.** The adopted 37-table change is genuine and must not be
   summarized as “still open.” The remaining fresh-catalog sentence is still
   a Major assurance-integrity defect. Final: PARTIALLY CLOSED plus F-52.
5. **F-44 sufficiency.** DP-3(a) can be reasonable under an enforced
   Okapi-only port; it does not establish that the target production cluster
   meets the assumption. The current YAML is an honest carrier, not proof of
   deployed enforcement.
6. **F-50 scope.** One view treats base-image currency as general maintenance.
   Security treats the actual year-old runtime in the candidate production
   artifact as a release Major. Final: Major and must-fix.
7. **D-34 impact.** The sampled official UI encodes its filter value, which
   supports the “well-behaved client” claim. The review did not prove that
   every FOLIO consumer does so.

## Final recommendation and must-fix-before-merge list

**NO-GO for PR merge and production cutover.**

Before merge:

1. Resolve F-46/F-47: reproduce the terminal-backslash and NUL matrices,
   choose parity or explicit governed behavior, register D-35/D-36 where
   applicable, and add discriminating IT/harness pins. Do not retain the
   pure-NUL 200-unfiltered path silently.
2. Resolve F-48 with canonicalized, conservative credential-name redaction
   and hostile regression rows for API key, auth token, JWT, authentication,
   encoded names, repeats and response headers.
3. Resolve F-49/F-44 by pinning the complete effective NetworkPolicy shape,
   rejecting additive/public exposure, and making a cluster-specific
   allow-Okapi/deny-non-Okapi test a release requirement.
4. Resolve F-50 by moving to a maintained Java 21 CPU level, pinning the base
   by immutable digest, scanning the final image and gating runtime age.
5. Resolve F-52 and F-51: add a fresh expected table census or narrow TRC-023,
   and link ADR-013 to the attestation architecture component.
6. Resolve F-53 by binding the lane to source revision plus JAR/image/
   descriptor digests or by building inside the lane.
7. Correct the completion residual list and closure claims to include the
   remaining deployment, provenance, traceability and parser risks.

Then rerun, from clean throwaway state:

- clean verify + final image scan/runtime version check;
- the expanded legacy/port tokenizer, wildcard, malformed-encoding and purge
  matrices;
- the full 45+ probe harness on legacy, fresh port and adopted port;
- the hostile proxy suite;
- Kubernetes routing and security-widening mutations against the final
  rendered cluster manifest;
- continuity/cache pins and semantic full gate with a fresh M9 sample;
- the artifact-identity sabotage plus a real-Okapi control/failure/rollback/
  repair/resume wave using the exact release artifact.

The next review can be targeted to F-46..F-53 plus the affected terminal
gates if the remediation remains minimal. A release decision before those
conditions are independently reproduced is not supported by this evidence.
