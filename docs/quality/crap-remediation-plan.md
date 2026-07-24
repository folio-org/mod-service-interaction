# CRAP Remediation Plan — mod-service-interaction

Source: `crap4java --verify` (patched to run the Maven `verify` phase so Failsafe
`*IT` coverage is included). Merged unit+IT coverage, production code only
(`src/main/java`). Baseline captured 2026-07-24.

- Methods analyzed (production): **224**
- Over CRAP threshold (>8.0): **23**
- Not exercised by any test (coverage N/A): **5**
- Max CRAP: **55.3** (`KiwtListing.coerce`)

## The governing identity

`CRAP = CC² · (1 − coverage)³ + CC`

At 100% coverage, `CRAP = CC`. Therefore:

- **CC ≤ 8** → threshold (8.0) is reachable by coverage alone. *Fix = add tests.*
- **CC ≥ 9** → CRAP > 8.0 **at any coverage**. *Fix = decompose, or formally accept.*

This splits every finding into exactly one of three buckets below.

Two CC-8 methods (`validateDefinition`, `parseSpecial`) sit at CRAP 8.03 — printed
as `8.0` in the report but strictly over — hence 23 over-threshold, not the 21
visible rows. They belong to Bucket A (coverage).

---

## Bucket A — Coverage gaps (CC ≤ 8): add tests, no code change

CRAP driven by missing coverage, not complexity. Tests alone bring each ≤ 8.0.
Ordered by leverage (lowest current coverage first).

| Method | Class | CC | Cov | CRAP | Target |
|---|---|---:|---:|---:|---|
| `deleteWidgetInstance` | controller.WidgetsController | 4 | 0% | 20.0 | edit-access delete + 403 + 404 paths |
| `updateDashboard` | controller.DashboardsController | 5 | 17% | 19.1 | update happy path + access denial |
| `resolveAccess` | service.dashboard.DashboardService | 5 | 32% | 12.8 | id-map / value-map / bare-string / null bindings |
| `buildSpecial` | web.KiwtListing | 6 | 60% | 8.2 | pinned grammar — see note ¹ |
| `probeAttribute` | web.KiwtListing | 8 | 82% | 8.4 | remaining attribute-probe branches ¹ |
| `bindSetting` | controller.RefdataSettingsController | 8 | 84% | 8.3 | create-vs-update + validation-failure branches |
| `hasAccess` | service.dashboard.DashboardService | 8 | 87% | 8.1 | view→edit→manage recursion + unknown-level default |
| `parseLeaf` | web.KiwtFilterParser | 8 | 91% | 8.1 | pinned grammar — see note ¹ |
| `validateDefinition` | service.widget.WidgetDefinitionService | 8 | 92% | 8.03 | invalid-schema / version-mismatch branches |
| `parseSpecial` | web.KiwtFilterParser | 8 | 92% | 8.03 | pinned grammar — see note ¹ |

Biggest wins are the three low-coverage controller/service methods
(`deleteWidgetInstance`, `updateDashboard`, `resolveAccess`): all pure authorization/
binding logic, no legacy-parity constraint, high CRAP purely from thin coverage.

¹ `KiwtListing` / `KiwtFilterParser` branches are **pinned legacy grammar**
(`KiwtListingGrammarIT` is the executable spec, incl. deliberately-preserved quirks).
Add coverage by extending `KiwtListingGrammarIT` with input/output pairs — do **not**
alter parser behavior.

## Bucket B — Irreducible complexity (CC ≥ 9): decompose or accept

Already well-covered (mostly ≥ 87%); CRAP is high because CC alone ≥ 9. Tests
cannot bring these under 8.0. Each needs a decision: **refactor** to lower CC, or
**formally accept** via a spec-level decision record.

| Method | Class | CC | Cov | CRAP | Disposition |
|---|---|---:|---:|---:|---|
| `tokenize` | web.KiwtFilterParser | 24 | 99% | 24.0 | **Accept** — pinned parser (ADR/grammar IT). Refactor risks parity. |
| `legalAbsorbedValue` | web.KiwtFilterParser | 22 | 84% | 24.0 | **Accept** (pinned) + top up coverage |
| `absorbEmptyRightSides` | web.KiwtFilterParser | 23 | 97% | 23.0 | **Accept** — pinned, incl. broken `$`-wildcard contract |
| `bindSequence` | controller.NumberGeneratorsController | 20 | 91% | 20.3 | **Refactor** — extract per-field binding; not parity-pinned |
| `updateUserDashboards` | service.dashboard.DashboardService | 16 | 100% | 16.0 | **Refactor** — extract reorder/default-election helpers |
| `updateAccessToDashboard` | service.dashboard.DashboardService | 14 | 100% | 14.0 | **Refactor** — split create-branch vs update-branch (legacy semantics documented; keep behavior) |
| `coerce` | web.KiwtListing | 13 | 37% | 55.3 | **Refactor + cover** — type-coercion switch; extract per-type coercers. Highest CRAP overall. |
| `buildOrientedComparison` | web.KiwtListing | 13 | 87% | 13.3 | **Accept** (pinned) or extract comparison-operator table |
| `buildTextMatch` | web.KiwtListing | 12 | 93% | 12.1 | **Accept** (pinned) |
| `purgeOnTheWire` | controller.TenantPurgeFlagAdvice | 11 | 90% | 11.1 | **Refactor** — extract purge-flag parsing from the advice body |
| `applySort` | web.KiwtListing | 11 | 99% | 11.0 | **Accept** (pinned) |
| `deriveMaximumCheck` | service.numgen.NumberGeneratorService | 10 | 100% | 10.0 | **Refactor** — extract max-value/threshold derivation |
| `calculate` | service.numgen.CheckDigitService | 9 | 85% | 9.3 | **Accept** — check-digit algorithms pinned by `NumberGeneratorParityIT`; top up coverage |

Note: `coerce` is the one method that is *both* high-CC and low-coverage — it leads
on refactor **and** coverage. Address it first regardless of bucket.

## Bucket C — Untested methods (coverage N/A): decide relevance

Not loaded by any test. Each is either a trivial framework shim (accept/ignore) or
a genuine gap (cover).

| Method | Class | CC | Note |
|---|---|---:|---|
| `setAsText` ×2 | controller.KiwtParamLeniencyAdvice | 5, 3 | `PropertyEditor` leniency shims — add a small `@WebMvcTest`/unit test exercising the lenient-parse paths |
| `getBody` / `getHeaders` | controller.TenantPurgeFlagAdvice | 1, 1 | Trivial wrapper accessors — cover incidentally when `purgeOnTheWire` is refactored/tested, else accept |
| `isValidAt` | service.attestation.KeyPairService | 2 | Key-validity window check — genuine gap; add a unit test (valid / expired / not-yet-valid) |

---

## Sequencing

1. ✅ **`coerce`** (CRAP 55.3) — refactored to a per-type coercer table (CC 13→4);
   pinned by `KiwtListingCoerceTest` (20 branch cases) + `KiwtListingGrammarIT`.
2. ✅ **Bucket A controllers/services** — `deleteWidgetInstance`, `updateDashboard`,
   `resolveAccess` (new `DashboardParityIT` @Order 11–13), `bindSetting`
   (`RefdataSettingsParityIT` @Order 5), `hasAccess` default arm
   (`DashboardParityIT` @Order 14), `validateDefinition` (new
   `WidgetDefinitionValidateTest`, all 5 branches).
3. ⏳ **Bucket C genuine gaps** — ✅ `isValidAt` (`SigningKeyValidityTest`);
   `KiwtParamLeniencyAdvice.setAsText` already driven by `ErrorEnvelopeMatrixIT`
   (`stats=notabool`/`perPage=abc`) — confirm on the gate before adding tests.
4. ✅ **Bucket B refactors** (non-pinned) — `deriveMaximumCheck` (extracted pure
   `maximumCheckValue`, CC 10→2, `MaximumCheckValueTest`), `bindSequence`
   (`copyIfPresent` + `resolveOwner`/`resolveCheckDigitAlgo`, CC 20→3),
   `purgeOnTheWire` (`scanTopLevelPurge`+`classify`, CC 11→3),
   `updateAccessToDashboard` (create/update item helpers, CC 14→3),
   `updateUserDashboards` (`ownsUpdatableItem`/`applyOrdering`/`clearOtherDefaults`,
   CC 16→3). All guardrail parity ITs stay green.
5. ✅ **Bucket A pinned-grammar coverage** — `KiwtListingGrammarIT` @Order 19–20
   add `is empty`/`is not empty`, malformed-clause, malformed-range, and
   dotted-scalar-path rows covering `buildSpecial`, `probeAttribute`, `parseLeaf`,
   `parseSpecial` — no parser behavior changed.
6. **Bucket B accepts** — for each pinned/parity method staying > 8.0 (the CC≥9
   `KiwtFilterParser`/`KiwtListing`/`CheckDigitService` methods), record a
   spec-level decision (via `sdd-session`) documenting *why* the complexity is a
   preserved contract. This is the governed equivalent of a CRAP suppression.

## Result (after steps 1–5)

Max CRAP **55.3 → 24.0**; production methods > 8.0 **23 → ~8**; production N/A
**5 → 0** (the 5 were a crap4java nested-class attribution bug — patched
separately, see below). Every remaining finding is a deliberate **accept**:

| Method | Class | CC | CRAP | Why it stays > 8.0 |
|---|---|---:|---:|---|
| `tokenize` | KiwtFilterParser | 24 | 24.0 | pinned tokenizer; parity IT is the spec |
| `legalAbsorbedValue` | KiwtFilterParser | 22 | 24.0 | pinned operator-absorption oracle (r31) |
| `absorbEmptyRightSides` | KiwtFilterParser | 23 | 23.0 | pinned empty-RHS absorption (D-32) |
| `buildOrientedComparison` | KiwtListing | 13 | 13.3 | pinned operator dispatch |
| `buildTextMatch` | KiwtListing | 12 | 12.1 | pinned match/term semantics |
| `applySort` | KiwtListing | 11 | 11.0 | pinned sort tolerance |
| `calculate` | CheckDigitService | 9 | 9.3 | pinned check-digit dispatch (NumberGeneratorParityIT) |
| `probeAttribute` | KiwtListing | 8 | 8.2 | CC8 but its null/empty-path guards and plural-descent branch are defensive — unreachable through the public grammar (the parser never passes an empty subject), so 100% coverage is not attainable without contorting inputs. Accept as-is. |

`updateAccessToDashboard` (was CC14) was split into two CC≤8 helpers; the new
`updateDashboardAccess` briefly showed 8.1 until its id-bearing edge branches
(nonexistent-id, caller's-own-id) were covered by `DashboardParityIT` @Order 13.

### crap4java tool fixes made during this work
- **`--verify` flag**: `CoverageRunner` ran only the Maven `test` phase; `--verify`
  runs `verify` so Failsafe `*IT` coverage is merged (this module is IT-heavy).
- **Nested-class attribution**: `CrapAnalyzer.lookupCoverage` matched coverage on
  the top-level class name only, so JaCoCo's `Outer$Inner`-keyed nested-method
  coverage never joined — every nested method reported N/A regardless of real
  coverage. Fixed by a `$`-boundary-aware `keyMatches`. This alone cleared all 5
  Bucket C N/As (`isValidAt`, both `setAsText`, `getBody`/`getHeaders`) — they
  were already covered; the tool simply mis-joined.

## Constraints (from CLAUDE.md — do not violate)

- **Pinned legacy behaviors are contracts, not bugs**: KIWT grammar (incl. broken
  `$`-wildcard), check-digit algorithms, missing PKs. Never "fix" them without a
  spec-level decision. Refactors here must be behavior-preserving and stay green
  against the parity ITs.
- **Spec is source of truth**: any accept-decision goes through a governed SDD
  session (`sdd-session`), never a hand-edit under `specs/`.
- **TDD**: characterization tests before any Bucket-B refactor; watch them pass
  before and after.

## Definition of done

- `crap4java --verify` reports **0 methods > 8.0** among non-accepted methods.
- Every method remaining > 8.0 has a committed decision record explaining the
  preserved-complexity rationale.
- No behavior change to any parity-pinned method (parity ITs stay green).
