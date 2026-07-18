# Wire-compatibility deviation dossier — Grails 4.4.x → Spring Boot 5.0.0

Every known behavioral difference between the legacy Grails module (`service/`,
4.4.0-SNAPSHOT) and the Spring Boot port (repo root, 5.0.0-SNAPSHOT), collected
across the migration milestones and verified empirically in M4 by booting the
**real legacy module**, populating a tenant through its REST API, and diffing
captured responses against the port running on the same database.

Ground truth: 13 endpoints captured on identical data — **8 byte-identical**,
**4 identical after array sort** (deviation D-4), **1 dead legacy endpoint**
(deviation D-1). Everything not listed below is byte-for-byte compatible,
including the refdata `owner` render, dashboard-access id-stub render,
`dateCreated` second-precision format, and id-less widget-definition renders.

Legend — **Impact**: does a well-behaved API consumer (ui-dashboard, other
FOLIO modules) observe the difference in practice?

## A. Legacy defects not ported (port behaves correctly on purpose)

### D-1 `GET /servint/widgets/instances/my-widgets` — dead in legacy
- **Legacy**: 500. The controller action is commented out; the route falls
  through to `show` with `id="my-widgets"`, `WidgetInstance.read` returns null,
  NPE in the access check.
- **Port**: 404 — the route does not exist and was never declared in the spec.
- **Impact**: none; no shipped consumer calls it (it has been broken in
  production for years).

### D-2 `POST /servint/widgets/definitions` — legacy 201 create / 500 duplicate vs port 405
- **Legacy** (R13 empirical, ×2 fresh tenants): a FRESH name+version pair
  creates the row and answers **201** with the created body (carrying no
  `id` — the gson view excludes it; the row is proven by filtered
  readback). An identical duplicate POST answers **500** `text/html`: the
  unique name+version constraint fires and the error render itself dies
  (`ConstraintViolationException` → `NoSuchMessageException 'unique'` →
  `ViewException`); the row count stays 1.
- **Port**: 405 — local-definition creation was never declared in the
  governed spec; definitions arrive via widget-type import and federation
  harvest only.
- **History**: an earlier revision claimed legacy 500s while creating the
  row; M4 had only ever exercised the DUPLICATE path (populate re-runs
  against an already-populated tenant), and R12 falsified a still-earlier
  port-201 claim. R13 resolved the contradiction as state-dependence, not
  environment-dependence (`docs/migration/evidence/r13-legacy/observations.md`).
- **Impact / consumer analysis**: definition provisioning in the shipped
  platform flows through widget-type import and the federation harvest;
  ui-dashboard only LISTS definitions when a widget is added and never
  POSTs one. The fresh-201 path was reachable but has no known consumer,
  and any retrying caller hit the 500 path. Declare the operation in
  `specs/api/servint-widgets.yaml` and implement it if a genuine
  local-definition workflow appears.
- **Evidence**: `docs/migration/evidence/r13-legacy/` run1 + run2
  (`widget-def-create` 201 fresh; `widget-def-create-duplicate` /
  `widget-def-create-retry` 500 with row count still 1).

### D-3 `GET /servint/widgets/instances/{id}` with unknown id
- **Legacy**: 500 (NPE on the null read).
- **Port**: 404.
- **Impact**: strictly better error semantics; state identical.

### D-20 `PUT /servint/dashboard/{id}/displayData` with a mismatched body `dashId`
- **Legacy**: accepted the write — authorization and lookup used the path
  dashboard id, then a non-null body `dashId` replaced the persisted row
  identity. Because `ddd_dash_id` is unique but has no FK, an edit-authorized
  caller could re-point their display-data row at ANY other dashboard
  (orphaning/reassigning it where the target lacked a row) — review finding
  F-09 reproduced this cross-dashboard write.
- **Port**: body `dashId` differing from the path id is rejected with 422 and
  the errors envelope (`code: "dashboard.id.mismatch"`), before any state
  change. An absent or path-matching body `dashId` behaves as before (the
  path identity wins; `layoutData` updates normally).
- **Impact**: none for well-behaved consumers — ui-dashboard sends the
  matching `dashId` (or none). Only the exploit-shaped request changes
  behavior. Deliberate divergence per remediation R8c; pinned by
  `DisplayDataIdentityIT`.

### D-27 `POST /servint/dashboard` answers 201 instead of legacy 200
- **Legacy** (R13 empirical, ×2 fresh tenants): dashboard create answers
  **200** with the created body — a bespoke Grails `respond` call, unlike
  the 201 the rest of the legacy module answers on creates.
- **Port**: 201 with the created body, per the governed spec's
  conventional create status.
- **Impact / consumer analysis**: ui-dashboard checks fetch `response.ok`
  (any 2xx) on dashboard creation and reads the body's `id`/`name`; no
  shipped consumer branches on 200-vs-201 here, and the body shape is
  identical (`{id, name, widgets:[]}`). Registered instead of silently
  diverging; `populate.sh` pins 200 (legacy) / 201 (port) per side.
- **Evidence**: `docs/migration/evidence/r13-legacy/` run1 + run2
  `dashboard-create` captures (200); the port's populate/capture runs
  (201). Review finding F-20.

## B. Nondeterminism pinned down

### D-4 Unsorted listing order
- **Legacy**: listings without an explicit `sort` return undefined
  (insertion/planner) order.
- **Port**: deterministic order.
- **Impact**: none for consumers that treat listings as sets (all known ones
  do). This is the entire delta on 4 of the 13 M4 capture endpoints — data and
  shape are identical after sorting the arrays.

### D-5 Attestation key cache keyed per tenant
- **Legacy**: module-level key-pair cache is tenant-blind (a `Map` keyed by
  usage only) — a cross-tenant key-leak bug under multi-tenant load.
- **Port**: cache keyed `tenant:usage`.
- **Impact**: wire-invisible except that the legacy bug cannot reproduce.

### D-21 Attestation key lifecycle and `kid` semantics (extends D-5)
- **Legacy** (and the port before remediation R8b): the cached value was a
  bare `KeyPair` — `kp_expires_at` was checked only when loading from the DB,
  so a cached key kept signing after expiry; purge/re-enable never evicted
  the cache, so a re-enabled tenant could sign with a key whose row had been
  dropped with the schema; and the JWT `kid` header was the usage/audience
  string (e.g. `extApp`), which cannot distinguish keys across rotation
  (review finding F-08).
- **Port**: on top of D-5's `tenant:usage` keying, each cache entry carries
  the backing `db_key_pair` row's id and `kp_expires_at`; validity is
  re-checked on EVERY read and an expired entry reloads from the DB
  (earliest-valid-first, creating a fresh key on demand — the existing
  semantics). Tenant purge evicts the tenant's cached keys
  (`ServintTenantService.afterTenantDeletion`). `kid` is now the signing
  key-record id (`kp_id`, a UUID): stable for as long as that key pair
  signs, different after any rotation, so receivers can select the exact
  public key unambiguously.
- **Impact**: the `kid` value change is the only wire-visible difference.
  No shipped receiver resolves keys by the legacy usage-string `kid` (the
  `aud` claim still carries the usage); rotation handling becomes
  deterministic. Pinned by `AttestationKeyLifecycleIT` and the updated
  `AttestationParityIT` kid assertions.

## C. Error-envelope shapes

### D-6 422 validation body
- **Legacy**: Grails `_errors.gson` render (field errors with Grails message
  codes and nested object metadata).
- **Port**: `{"errors":[{"code","message"}]}`.
- **Impact**: status code and trigger conditions are identical (GORM-style
  create/update validation → 422); only the JSON body shape differs. No known
  consumer parses the body.

### D-7 Bean-validation 400 body
- **Legacy**: Grails binding errors surfaced via its own error render.
- **Port**: Spring's default problem shape for `@Valid` failures.
- **Impact**: same trigger conditions, different body. No known consumer
  parses it.

### D-8 `editUserDashboards` 400/403 responses carry no message body
- **Legacy**: `sendError` with a short text message.
- **Port**: bare status.
- **Impact**: ui-dashboard keys off the status code only.

### D-22 Malformed JSON request body → 400 `malformed.json` envelope
- **Legacy**: a syntactically broken JSON body routes through Grails'
  data-binding source creation; review finding F-14 observed behavior that
  differs from the port (statuses captured in the review's scratch `*-extra/`
  runs, which were not retained — the legacy-side harness pin stays `*` until
  the next legacy baseline capture).
- **Port**: deterministic 400 with
  `{"errors":[{"code":"malformed.json","message":"JSON request body could not
  be parsed"}]}` on every unparseable POST/PUT body
  (`HttpMessageNotReadableException` handler) — never a raw Spring
  whitelabel/500, and no Jackson parse detail (body excerpts, byte offsets)
  on the wire.
- **Impact**: none for consumers sending valid JSON; body-shape parsing is
  already the D-6/D-7 class of difference.
- **Evidence**: `ErrorEnvelopeMatrixIT.malformedJsonBodyAnswers400WithEnvelope`;
  review F-14; remediation R9.

### D-23 Missing `x-okapi-tenant` header → folio-spring 400 text/plain
- **Legacy**: grails-okapi resolves the tenant per request in
  `OkapiTenantResolver`; a missing header throws GORM's
  `TenantNotFoundException` ("Tenant could not be resolved from HTTP
  Header:"), surfacing as a Grails 500-class server error (review F-14
  observed the divergence).
- **Port**: folio-spring's `TenantOkapiHeaderValidationFilter` answers 400,
  `text/plain`, body `x-okapi-tenant header must be provided` — the
  framework-standard shape, on every route except the `/admin` base path.
- **Impact**: none in production — Okapi always injects the header; only
  direct callers and tooling see the difference, and 400 is the correct
  answer for a request the module cannot attribute to a tenant.
- **Evidence**:
  `ErrorEnvelopeMatrixIT.missingTenantHeaderAnswers400WithFolioSpringMessage`.

### D-24 DB integrity conflict → sanitized 409 `integrity.violation` envelope
- **Legacy**: no GORM `unique` constraint backs the DB uniques (e.g.
  `number_generator.ng_code`'s `NumberGeneratorUniqueCode`,
  `number-generator-model.groovy`), so a duplicate-code POST passed
  validation, hit the constraint at flush and answered a Grails 500; the
  review additionally observed raw constraint detail reaching the wire in
  this failure class (F-14; the pre-R6 first-use race).
- **Port**: every `DataIntegrityViolationException` surfacing from user input
  (unique violations, FK conflicts) answers 409 with
  `{"errors":[{"code":"integrity.violation","message":"Request conflicts with
  existing data"}]}` — a fixed message; SQL state, constraint names and
  driver detail never reach the wire. The state outcome matches legacy: the
  transaction rolls back, no row is written.
- **Impact**: consumers that treated the legacy 500 as failure now see a
  clearer failure; valid writes are unchanged.
- **Evidence**: `ErrorEnvelopeMatrixIT.duplicateGeneratorCodeAnswersSanitized409`
  (asserts no `duplicate key`/constraint-name/SQL fragments in the body).

## D. Request-binding differences

### D-9 Partial PUT null handling
- **Legacy**: binds every property present in the JSON, including explicit
  `null` (nulls the column).
- **Port**: binds only non-null present fields; explicit `null` keeps the
  stored value.
- **Impact**: only for clients that deliberately send `"field": null` to clear
  a value. ui-dashboard does not.

### D-10 Widget instance PUT with explicit `"weight": null`
- **Legacy**: recomputes the weight.
- **Port**: keeps the existing weight.
- **Impact**: corner of D-9; no known caller sends it.

### D-11 `checkDigitAlgo` bare-string binding
- **Legacy**: kiwt binds a refdata association from a bare string (id **or**
  value), e.g. `"checkDigitAlgo": "ean13"`.
- **Port**: binds the object form `{"id": …}` / `{"value": …}` only.
- **Impact**: ui-service-interaction submits the object form. A custom
  deserializer can close this if a string-submitting consumer appears.

### D-25 Unknown refdata value in a body → 400 instead of a silent drop
- **Legacy**: web-toolkit's `RefdataBinding` resolves a submitted refdata
  reference by id, then by value, via a plain lookup (not lookupOrCreate); an
  unknown reference bound **null** and the write succeeded — e.g. a sequence
  created with `"checkDigitAlgo": {"value": "nope"}` answered 201 with the
  algorithm silently dropped (`checkDigitAlgo` is nullable).
- **Port**: 400 with `{"errors":[{"code":"unknown.refdata","message":"Unknown
  check digit algorithm refdata: <id-or-value>"}]}`, before any state change.
- **Impact**: silently dropping a requested check-digit algorithm would
  generate numbers without the check digits the caller asked for — the
  explicit reject is a deliberate correctness improvement.
  ui-service-interaction submits values read from the refdata endpoint, so no
  shipped consumer sends unknown values.
- **Evidence**: `ErrorEnvelopeMatrixIT.unknownRefdataValueAnswers400WithEnvelope`;
  review F-14; remediation R9.

## E. Listing-parameter coverage

### D-12 Sub-route listings ignore kiwt query params
- **Surfaces**: `/servint/dashboard/my-dashboards`,
  `/servint/dashboard/{id}/users`, `/servint/dashboard/{id}/widgets`.
- **Legacy**: honored the full kiwt param set (filters/sort/paging) even on
  these bespoke routes.
- **Port**: fixed page size 10, no params (the spec never declared them).
- **Impact**: dashboards per user and users per dashboard are small; no
  consumer pages these routes. Declare the params in the spec and wire kiwt
  listing through if a >10 case materializes.

### D-13 Refdata lookup domain registry
- **Legacy**: `/servint/refdata/{domain}/{property}` resolves *any* Grails
  domain class, including web-toolkit custom-property entities.
- **Port**: fixed map of the 13 legacy entity simple names (case-insensitive);
  anything else → 404.
- **Impact**: all shipped consumers use the 13 mapped domains. (Note the M3
  claim that this endpoint was broken in legacy was **falsified** in M4 — it
  works, and the port matches its real behavior byte-for-byte.)

### D-14 kiwt stats envelope extras
- **Legacy**: stats mode may attach extra bookkeeping fields beyond
  `totalRecords`/`results` on some listings.
- **Port**: envelope fields pinned by the M4 captures and parity ITs match;
  fields outside those captures are not fixture-pinned.
- **Impact**: low; `totalRecords` + `results` are what consumers read.

### D-19 Listing grammar: N-ary boolean chains and group execution
- **Legacy**: the web-toolkit listener pairs only the top two criteria at
  each `&&`/`||` exit and AND-drains leftovers, so a same-level chain of
  three or more `||` terms can evaluate as `a AND (b OR c)` instead of a
  flat disjunction; parenthesized groups execute as correlated id-subqueries.
- **Port**: same-level chains associate as a flat N-ary conjunction or
  disjunction (the accidental pairing quirk is deliberately not replicated,
  REQ-022 AC2); groups execute as nested JPA predicates, which is
  observably equivalent for the module's to-one association paths.
- **Impact**: only requests sending 3+ same-level `||`/`&&` terms could
  observe a difference — and for those the port's answer is the boolean
  reading any consumer would expect. No shipped consumer sends compound
  filters on these endpoints.
- **Evidence**: web-toolkit-ce 10.6.4 `SimpleLookupServiceListenerWtk`
  (`exitConjunctiveFilter`/`exitDisjunctiveFilter` top-two pairing);
  `KiwtListingGrammarIT` (R5 probe matrix).

### D-28 Invalid boolean filter value: legacy 500 vs port coerce-to-false 200
- **Legacy** (R13 ×2): `filters=enabled==notabool` → **500**
  (`ConversionFailedException` in the log; the uncaught error answers the
  101-byte "Uncaught Internal server error" envelope).
- **Port**: `Boolean.valueOf` coerces any non-"true" value to false, so the
  filter behaves as `enabled==false` and answers 200.
- **Impact**: strictly better availability on garbage input — per the
  mandate, legacy 500s on malformed input are defects not to be
  reproduced. Governed by REQ-022 AC4; pinned by `ErrorEnvelopeMatrixIT`,
  `KiwtListingGrammarIT`, and probe `f14-invalid-boolean`.
- **Evidence**: `docs/migration/evidence/r13-legacy/observations.md`
  (F-21 table, `f21-invalid-boolean`).

### D-29 — RETIRED (M9 R31): backslash values are now legacy-identical
- **Was**: the port's filter tokenizer unescaped `\x` sequences, so
  `code==pl\ain` matched the `plain` row while legacy (which never
  unescapes) answered 0 rows.
- **Retired 2026-07-23**: the r31 oracle
  (`docs/migration/evidence/r31-escaped-oracle`, parse trees + SQL binds)
  proved legacy keeps every backslash as raw literal value text, and F-38
  made the unescaping untenable — it infected every escaped-token row of
  the absorption matrix. The port tokenizer now retains backslashes
  verbatim (REQ-022 AC7), so `code==pl\ain` matches only a row literally
  holding `pl\ain` — **parity, no deviation remains**. Pinned by
  `KiwtListingGrammarIT` (`f21DirectedRowsBehaveAsGoverned`,
  `escapedTokensAreRawLiteralValueText`) and probe `f21-escaped-literal`
  (now expected byte-identical on both sides).

### D-30 Malformed or uncoercible filter input: legacy 500 vs port clause-drop 200
- **Legacy** (R13 ×2): `filters=(code==plain` (unbalanced parenthesis)
  → 500; `filters=nextValue>notanumber` (uncoercible numeric) → 500.
  Extended by the r31 oracle (M9): a single non-absorbable operator whose
  tail is NOT identifier-shaped — `code==alpha>5` — answers an uncaught
  **500** (oracle s8; the identifier-tail shapes `code==alpha<beta` /
  `code==alpha=beta` answer 400 `Invalid property: <tail>` and the port
  MATCHES those, REQ-022 AC7).
- **Port**: the offending filters parameter is dropped and the request
  answers 200 with the remaining filtering applied (REQ-022 AC4/AC7
  tolerance — the same behavior legacy itself shows for empty-right-side
  clauses).
- **Impact**: strictly better availability; per the mandate legacy 500s on
  malformed input are defects not to be reproduced. Pinned by
  `KiwtListingGrammarIT` and probes `f21-unbalanced-paren` /
  `f21-gt-notanumber` / `r31-single-gt-numeric`.
- **Evidence**: `docs/migration/evidence/r13-legacy/observations.md`
  (F-21 table); `docs/migration/evidence/r31-escaped-oracle/` (Phase S,
  s3/s6/s8/s9 with parse trees).

### D-31 Oversized (10k-char) filter value: both sides 400, envelopes differ
- **Legacy** (R13 ×2): 400 with an EMPTY body and no Content-Type — the
  container rejects the oversized request line before the application runs.
- **Port**: 400 at the container level with the Spring error body
  (~435 bytes).
- **Impact**: same status on pathological input; envelope-only divergence
  that no consumer parses. Wire-level pin only (probe `f21-10k-filter`) —
  MockMvc bypasses the container, so no in-suite IT can carry this row.
- **Evidence**: `docs/migration/evidence/r13-legacy/observations.md`
  (F-21 table); re-review №2 port-side measurement (435-byte body).

### D-32 Empty right side inside a compound: legacy greedy absorption replicated; negation-empty 500 not replicated
- **Legacy** (R22 oracle, booted 4.4.0-SNAPSHOT, tenant r22o, statuses
  stable on re-run): a `&&`/`||` expression containing a comparison with an
  empty right side is never evaluated as a compound — the grammar's greedy
  value consumption re-reads the text as literal value content. Trailing
  empty leaf: the whole (paren-stripped) parameter collapses into one
  comparison of the first subject/operator against the literal remainder —
  `code==alpha&&prefix==` behaves as `code == "alpha&&prefix=="` (200 `[]`,
  and it MATCHES a row whose code is literally that string; same for
  `(code==alpha||prefix==)` and the no-paren form). Empty leaf followed by
  more text: that leaf absorbs the remainder —
  `code==alpha&&prefix==&&code==alpha` behaves as `code==alpha` AND
  `prefix == "&&code==alpha"`, answering 400
  `Invalid property: prefix` on generators (prefix is not a generator
  property) and 200/0 rows on sequences. `!(prefix==)` answers **500**.
  Uncoercible values answer 500 alone or inside compounds (D-30's class;
  oracle probes X1–X3).
- **Port**: replicates the absorption semantics exactly (pre-pass in
  `KiwtFilterParser.absorbEmptyRightSides`), including the 400 for unknown
  absorbed subjects and the absorbed-literal matches. The
  negation-with-empty-RHS 500 is a legacy crash defect deliberately not
  replicated — the port drops the whole parameter and answers 200. On the
  400 path each side answers its platform envelope (legacy
  `SimpleLookupService` message with timestamp; port `errors[]` with
  `invalid.property`) — status and the `Invalid property: <subject>`
  semantic match.
- **Impact**: byte-parity on every measured 200 shape; status parity on the
  400 shapes (envelope-only divergence); 500→200 only for `!`-with-empty
  and coercion-in-compound (strictly better availability, the mandate's
  defect-not-reproduced class). Governed by REQ-022 AC6 (absorption), AC1
  (top-level drop precedes property validation and join planning), AC4
  (whole-parameter coercion drop). Pinned by `KiwtListingGrammarIT`
  (`emptyRightSidesInsideCompoundsAbsorbGreedily`,
  `emptyRightSideDropsBeforeValidationAndJoins`) and probes `r22-*`.
- **Evidence**: `docs/migration/evidence/r22-legacy-compound/` (27-probe
  matrix + absorption-discriminator rows + re-runs; README results tables).

### D-33 Repeated `term` parameters: legacy 500 vs port comma-joined 200
- **Legacy** (r32 oracle, booted 4.4.0-SNAPSHOT, tenant r31o):
  `match=code&term=ab%cd&term=abcd` → **500** `Uncaught Internal server
  error` (oracle t6) — legacy's getTextMatches cannot take two term
  parameters.
- **Port**: Spring binds the repeated values into one comma-joined string
  and evaluates it as a single term — 200.
- **Impact**: no shipped consumer sends more than one `term` (the UI sends
  exactly one); the divergence converts a legacy crash into a well-defined
  answer — the mandate's defect-not-reproduced class. Pinned by
  `KiwtListingGrammarIT` (`wildcardSemanticsPerLookupPath`) and probe
  `r32-match-multiterm`.
- **Evidence**: `docs/migration/evidence/r32-wildcard-oracle/`
  (`t6-match-multiterm.json`).

## F. Platform surface and schema residue

### D-15 `/_/tenant` implementation
- **Legacy**: grails-okapi tenant endpoint (`_tenant` 1.2).
- **Port**: folio-spring `TenantController` — the `_tenant` **2.0** contract;
  see D-17 for the deliberate interface upgrade.
- **Impact**: Okapi drives this; response body details differ per platform
  convention.

### D-17 `_tenant` interface upgraded 1.2 → 2.0 (deliberate; ADR-012)
- **Legacy**: `_tenant` 1.2 — `POST /_/tenant` (enable/upgrade, 200),
  `DELETE /_/tenant` (purge), `POST /_/tenant/disable` (soft disable).
- **Port**: `_tenant` 2.0 — `POST /_/tenant` (enable/upgrade, disable via
  `module_from` + blank `module_to` + `purge=false` — see D-26 — **and**
  purge via blank `module_to` + `purge=true`; 204 on synchronous
  completion), `GET /_/tenant/{id}` (200 `"true"` — every job completes
  synchronously), `DELETE /_/tenant/{id}` (204 no-op). The legacy 1.2
  routes are neither declared nor served.
- **Why not 1.2 parity**: folio-spring implements only the 2.0 contract and
  both reference modules (mod-consortia-keycloak, mod-users-keycloak) declare
  2.0. The interface's sole consumer is Okapi, which drives enable/disable/
  purge from the *installed* descriptor — after cutover the 2.0 routes are
  the ones called, so re-implementing 1.2 against the framework grain would
  produce dead code with real risk. Decision record: ADR-012
  (`specs/decisions/012-tenant-interface-2-0.yaml`); review finding F-02.
- **Impact**: none for Okapi-mediated operation (Okapi reads the interface
  version from the descriptor and uses the matching protocol). Automation
  that bypassed Okapi and called `POST /_/tenant/disable` or bare
  `DELETE /_/tenant` directly would break; no such consumer is known.
- **Evidence**: `TenantEnableIT` (enable 204 + legacy-named schema, idempotent
  repeat, GET/DELETE operation routes as declared, side-effect-free disable
  per D-26, purge drops the schema).

### D-26 Disable is a delivered `POST /_/tenant` job answered as a near-no-op
- **Legacy**: `_tenant` 1.2 soft disable was its own route
  (`POST /_/tenant/disable`).
- **Port**: under `_tenant` 2.0 Okapi delivers a disable as
  `POST /_/tenant` `{module_from, module_to: <blank>, purge: false}` —
  verified at source: Okapi `TenantManager` (commit `dd321ba`), the `"2.0"`
  interface case, always sends the `purge` flag explicitly
  (enable/upgrade `{module_to, [module_from], purge:false}`, disable
  `{module_from, purge:false}`, purge `{module_from, purge:true}`).
  folio-spring-base 10.0.0 routes `isDisableJob = blank module_to && purge`,
  so its stock handling would treat the disable shape as an upgrade and
  re-run Liquibase plus the seeding hooks. The port intercepts the shape in
  `ServintTenantController.postTenant` and answers **204 with no Liquibase,
  no seeding, schema and data untouched**; only the tenant's module-level
  caches (widget definitions, signing keys) are evicted
  (`ServintTenantService.disableTenant`), so a re-enable starts cold.
  No destructive default (review №3 F-32, R25): `TenantAttributes.purge`
  defaults `true`, so a hand-crafted blank-`module_to` body that *omits*
  `purge` used to bind as a purge job and silently drop the schema. The
  port now restores the wire-level flag (`TenantPurgeFlagAdvice` resets the
  bound value to null when the JSON carried no non-null `purge`) and
  rejects that shape **400** `purge.not.explicit` before any tenant work —
  purge requires explicit `purge=true`, disable explicit `purge=false`
  (REQ-020 AC6). Okapi never omits the flag on 2.0 (all five R20-rehearsal
  bodies carry it), so the rejection is unreachable on an Okapi-driven
  lifecycle.
- **Why**: an earlier revision of ADR-012/D-17 wrongly claimed a plain
  disable never reaches the module (re-review №2 finding F-18, REGRESSED
  F-02); the corrected model is pinned in ADR-012 and REQ-020 AC4.
- **Impact**: none for Okapi-mediated operation — a disable now has zero
  data side effects (previously, via the stock path, it would have re-run
  migrations and re-seeded deleted reference values).
- **Evidence**: `TenantEnableIT.okapiShapedDisableIsSideEffectFree` (204;
  business row survives; `databasechangelog` count unchanged; a seeded
  refdata value deleted pre-disable stays deleted);
  `TenantEnableIT.omittedPurgeWithBlankModuleToIsRejected` (flagless,
  `{}`, and `purge: null` bodies all 400 `purge.not.explicit`; schema and
  changelog count untouched); and the R20 real-Okapi
  rehearsal (`docs/migration/evidence/r20-rehearsal/`, Okapi 7.0.6 dev
  mode): all five observed `/_/tenant` bodies — legacy 1.2 install, port
  upgrade/disable/re-enable/purge — match the shapes above verbatim
  (`tenant-calls/`), and the disable left the adopted schema at 14
  `MARK_RAN` changelog rows with all business row counts unchanged
  (`psql/`), with the `_timer` route registered (`timers.json`). The M8
  R-CERT round re-confirmed all of this against the artifact rebuilt from
  cert HEAD (`docs/migration/evidence/rcert/`): five more Okapi 7.0.6
  upgrade bodies with explicit `purge: false` plus the rollback's inverse
  transition (`okapi-rollout/tenant-calls-*/`), and the full explicit-purge
  lifecycle over the wire — flagless/`{}`/`purge: null` all 400 with zero
  side effects, explicit `purge: true` the only path that drops the schema
  (`lifecycle/`).

### D-18 Seeding trigger truthiness + dropped sample-only test rows
- **Legacy**: grails-okapi treats a `loadReference`/`loadSample` tenant
  parameter as truthy for **any** value except case-insensitive `"false"`
  (`"TRUE"`, `"1"`, `"yes"` all seed). Its `loadSample` path also seeded a
  `test_app_setting` app-setting row and a `test` custom-property definition
  alongside the widget types.
- **Port**: folio-spring's `TenantController` honors only the literal string
  `"true"` (case-sensitive) — the framework contract; other values are
  treated as absent. The sample load imports only the widget types; the
  legacy test rows are web-toolkit residue with no port-side machinery
  (see D-16) and are deliberately not ported.
- **Impact**: Okapi-driven installs pass exactly `"true"`/`"false"`, so no
  observable difference in practice; automation sending exotic truthy
  values would seed on legacy but not on the port. No known consumer.
- **Evidence**: `TenantSeedingMatrixIT` (R4 enable matrix); review F-06.

### D-16 Schema residue in adopted tenants (deliberately untouched)
- 22 web-toolkit `custom_property*` tables, `tenant_changelog` +
  `tenant_changelog_lock` (legacy Liquibase state), and legacy-seeded rows
  (`test/test_app_setting` app-setting, a custom-property definition) remain
  in adopted schemas. The port neither reads nor drops them; keeping
  `tenant_changelog` intact is what makes rollback trivial (see the runbook).
- Fresh (post-cutover) tenants get only the port-owned tables — identical
  DDL, constraint names included, to legacy for those tables (physical
  column order excepted, next bullet) — and none of the residue.
- **Benign column-ordinal delta (review F-10)**: in fresh port-created
  schemas `dashboard.dshb_description` sits at `ordinal_position` 4; in
  legacy schemas it is 5, because legacy added the column in a later
  changelog (`service-interaction-1-3.groovy`) after `dshb_owner_fk` was
  dropped. Column names, types, nullability, constraints, and row data are
  identical — only the physical ordinal differs. Catalog comparisons must
  therefore compare column sets sorted by name, not by ordinal (the
  runbook's verification SQL does).

### D-34 Malformed percent-escape in a query string: legacy 200 empty vs port container-level 400
- **Legacy** (R-VER oracle re-run, booted 4.4.0-SNAPSHOT, tenant `r31o`):
  the raw query `filters=code%3D~ab%cd` — what a naive client sends when it
  fails to encode `%` — carries `%cd`, a syntactically valid percent-escape
  that decodes to the lone byte `0xCD` (invalid as standalone UTF-8).
  Legacy's container (Tomcat 9) decodes it leniently; the filter value never
  matches anything and the listing answers **200 with 0 rows** (oracle probe
  `r1-raw-unencoded-pct`).
- **Port**: embedded Tomcat 10.1 (Spring Boot 4.0) enforces strict URI
  decoding and rejects the request at the container, before any module code
  runs — **400** with the platform's default error body
  (`{"timestamp":…,"status":400,"error":"Bad Request","path":…}`), not the
  servint errors envelope.
- **Impact**: reachable only by a client that emits raw unencoded `%` in a
  query string — already malformed per RFC 3986; every well-behaved consumer
  percent-encodes and is unaffected (the encoded form `ab%25cd` is parity,
  probe `r32-contains-pct` byte-equal). The divergence converts silent
  garbage-in/empty-out into an explicit 400. Not addressable in module code:
  the rejection happens in the container's URI processor. MockMvc-based ITs
  cannot observe it (no container); pinned at the wire by the R-VER oracle
  re-run.
- **Evidence**: `docs/migration/evidence/rver/oracle-rerun/`
  (`legacy/r1-raw-unencoded-pct.json` 200 `[]`,
  `port/r1-raw-unencoded-pct.json` 400, `compare.txt`).
