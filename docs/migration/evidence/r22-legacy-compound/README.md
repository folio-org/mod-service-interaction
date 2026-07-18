# r22-legacy-compound — legacy compound / empty-RHS filter semantics (R22 Phase 1)

Empirical oracle run against the BOOTED legacy Grails module answering review
finding F-26: legacy answered 200 + empty array for `code==alpha&&prefix==` and
`(code==alpha||prefix==)` while each component alone was parity-clean. This
bundle pins WHAT legacy does (and, via directed fixture rows, WHY). It records
legacy ground truth only — no conclusions here about what the port should do.

## Rig identification

| Item | Value |
|---|---|
| Legacy artifact | `service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar` |
| Jar sha256 | `abb3721190394d2eea3e5661345b4d0fd8da89add558e0fc9924b26ec79b4c57` (identical to the R13 rig) |
| Boot (observed via ps) | `java -Ddb.host=localhost -Ddb.port=54321 -Ddb.database=okapi_modules_test -Ddb.username=folio_admin -Ddb.password=folio_admin -Dserver.port=8080 -jar <jar>` |
| Filter engine | `com.k_int.grails:web-toolkit-ce:10.6.4` — `SimpleLookupService` + ANTLR grammar `SimpleLookupWtk.g4` |
| Tenant | `r22o`, fresh: `POST /_/tenant` body `{"module_to":"mod-service-interaction-4.4.0-SNAPSHOT","parameters":[{"key":"loadReference","value":"true"}]}` → **201** (`fixture/tenant-create-response.json`) |
| Date | 2026-07-21 |

Operational rig notes observed during setup (not probes):

- A tenant header WITHOUT a space after the colon (`-H 'X-Okapi-Tenant:r22o'`)
  is answered `500 Uncaught Internal server error` — indistinguishable from the
  pinned missing-tenant behavior. Every evidence call uses `X-Okapi-Tenant: r22o`.
- `stats=true` works on both listings (200, envelope keys
  `meta,page,pageSize,results,total,totalPages,totalRecords`) —
  `fixture/*-unfiltered-stats.json`.

## Fixture

`harness/populate.sh` (SIDE=legacy, TENANT=r22o) ran clean — all steps passed;
per-step bodies in `populate-legacy/`. Additions beyond populate.sh, all via
REST (each create body + status in `probes-log.txt`):

| Added before the matrix | Why |
|---|---|
| generator `{"code":"alpha","name":"alpha"}` | populate.sh creates no `alpha` code |
| sequence `emptypfx` with `"prefix":""` | empty-string-prefix attempt — **stored as NULL** (Grails binding converts `""` to null; DB-audited). An empty-string prefix CANNOT exist via the API. |
| Added for phase 2 (absorption proof) | |
| generator `code = "alpha&&prefix=="` | literal equal to P1's full RHS remainder |
| generator `code = "alpha\|\|prefix=="` | literal equal to P2/D2's full RHS remainder |
| sequence `absorbpfx`, `prefix = "&&code==plain"` | literal equal to SD1's absorbed value |

Baselines: at matrix time generators=10, sequences=12; after phase-2 creates
generators=12 (`dx1`), sequences=13. Bodies in `fixture/`.

Sequence `prefix` distribution (DB audit `fixture/db-prefix-checkdigit-audit.txt`):
NULL on 10/12 at matrix time, `"m4-"` on `plain`, **zero empty-string rows**
(post-phase-2: `absorbpfx` = `"&&code==plain"`). `checkDigitAlgo` (refdata):
NULL on `plain` + `emptypfx`, `ean13` on 4, `none` on 6. Dotted path
`checkDigitAlgo.value` resolves on the sequences listing (C7 → 4 rows).

**Model caveat:** `prefix`/`checkDigitAlgo` are properties of
NumberGeneratorSequence only. On `/servint/numberGenerators` the `prefix`
probes exercise *unknown-property* semantics; the `s*`/`sd*` rows repeat the
matrix on `/servint/numberGeneratorSequences` where `prefix` is a real
nullable property.

## Mechanism (grammar-derived, then empirically proven)

In `SimpleLookupWtk.g4` the token set of `value_exp` excludes only
`! ( ) > < =`(single)` \` — `&&`, `||`, `==`, `!=` etc. are legal INSIDE a
value. A comparison with an empty RHS is unparseable as a leaf, so:

- **Alone / whole string unparseable** → no criterion built → filter silently
  dropped → unfiltered listing (P3/P6/P10/P4; re-confirms
  `r13-legacy/run3-r16-empty-rhs`).
- **Inside a compound** → the compound parse fails and the string re-parses as
  ONE comparison whose value literal ABSORBS the operators and everything after
  them: `code==alpha&&prefix==` ⇒ `code == "alpha&&prefix=="`. There is no
  boolean collapse and no clause-local drop — the empty array is that single
  weird equality matching nothing. **Proof:** after creating rows whose
  code/prefix literally equal the absorbed strings, the same probes return
  exactly those rows (dx2–dx5).
- When the absorbed re-parse puts an **invalid property on the left**
  (`prefix==&&code==alpha` on generators ⇒ subject `prefix`) → 400
  `Failure in SimpleLookupService. Invalid property: prefix`.
- Three leaves with the middle one empty parse as `first && (second-absorbs-third)`:
  `code==alpha && prefix=="&&code==alpha"` — 400 on generators (invalid
  property), 200/0 on sequences.
- Empty leaf under `!(...)` → 500 `Uncaught Internal server error` (P8/S8).
- **Coercion failures are different:** the compound PARSES fine, then Long
  coercion of the RHS throws → 500 — identical alone and inside a compound
  (X1–X3; top-level D-30 re-confirmed).

## Results

Matrix-time baselines: generators 10, sequences 12. `rows` `-` = error body
(all error bodies are the module JSON envelope `{"error":<code>,"timestamp":…,"message":…}`).

| Probe | Endpoint | Decoded filter | HTTP | Rows | Interpretation (legacy ground truth) |
|---|---|---|---|---|---|
| c1 | gen | *(unfiltered)* | 200 | 10 | baseline |
| c2 | gen | `code==alpha` | 200 | 1 | control |
| c3 | gen | `code==alpha&&code==alpha` | 200 | 1 | valid AND compound works |
| c4 | gen | `code==alpha\|\|code==zzznot` | 200 | 1 | valid OR compound works |
| c5 | seq | *(unfiltered)* | 200 | 12 | baseline |
| c6 | seq | `code==plain` | 200 | 1 | control |
| c7 | seq | `checkDigitAlgo.value==ean13` | 200 | 4 | dotted refdata path resolves |
| p1 | gen | `code==alpha&&prefix==` | 200 | 0 | single absorbed comparison `code=="alpha&&prefix=="` (proof: dx2) |
| p2 | gen | `(code==alpha\|\|prefix==)` | 200 | 0 | group around absorbed `code=="alpha\|\|prefix=="` (proof: dx3) |
| p3 | gen | `prefix==` | 200 | 10 | unparseable → whole filter dropped → unfiltered |
| p4 | seq | `checkDigitAlgo.value==` | 200 | 12 | dropped; count == baseline ⇒ **no join residue** |
| p5 | seq | `code==plain&&checkDigitAlgo.value==` | 200 | 0 | absorbed `code=="plain&&checkDigitAlgo.value=="` |
| p6 | gen | `notAProp==` | 200 | 10 | unparseable → dropped; unknown property never validated |
| p7 | gen | `code==alpha&&notAProp==` | 200 | 0 | absorbed `code=="alpha&&notAProp=="` |
| p8 | gen | `!(prefix==)` | 500 | – | empty leaf under NOT → uncaught server error |
| p9 | gen | `code==alpha&&prefix==&&code==alpha` | 400 | – | parses `code==alpha && prefix=="&&code==alpha"`; `Invalid property: prefix` |
| p10 | gen | `code==` | 200 | 10 | unparseable → dropped |
| d1 | gen | `prefix==&&code==alpha` | 400 | – | absorbed `prefix=="&&code==alpha"`; `Invalid property: prefix` |
| d2 | gen | `code==alpha\|\|prefix==` | 200 | 0 | absorbed `code=="alpha\|\|prefix=="` (proof: dx4) |
| d3 | gen | `code==zzznot\|\|prefix==` | 200 | 0 | absorbed `code=="zzznot\|\|prefix=="` |
| d4 | gen | `notAProp==x` | 400 | – | parseable leaf, unknown property → `Invalid property: notAProp` |
| s1 | seq | `code==plain&&prefix==` | 200 | 0 | absorbed `code=="plain&&prefix=="` |
| s2 | seq | `(code==plain\|\|prefix==)` | 200 | 0 | absorbed inside group |
| s3 | seq | `prefix==` | 200 | 12 | dropped (run3-r16-empty-rhs re-confirmed) |
| s8 | seq | `!(prefix==)` | 500 | – | as p8 |
| s9 | seq | `code==plain&&prefix==&&code==plain` | 200 | 0 | `code==plain && prefix=="&&code==plain"` — both valid on seq, no row matches both |
| sd1 | seq | `prefix==&&code==plain` | 200 | 0 | absorbed `prefix=="&&code==plain"` (proof: dx5) |
| sd2 | seq | `code==plain\|\|prefix==` | 200 | 0 | absorbed |
| sd3 | seq | `code==zzznot\|\|prefix==` | 200 | 0 | absorbed |
| x1 | seq | `code==plain&&nextValue==abc` | 500 | – | compound parses; Long coercion of `abc` throws → 500 |
| x2 | seq | `nextValue==abc` | 500 | – | top-level coercion failure (D-30 re-confirmed) |
| x3 | seq | `code==plain&&nextValue>notanumber` | 500 | – | coercion failure inside compound → 500 |

### Phase 2 — absorption proof + stability (after the three dx0 creates; gen baseline 12, seq 13)

| Probe | Endpoint | Decoded filter | HTTP | Rows | Matched codes |
|---|---|---|---|---|---|
| dx1 | gen | *(unfiltered)* | 200 | 12 | post-create baseline |
| dx2 | gen | `code==alpha&&prefix==` | 200 | 1 | `["alpha&&prefix=="]` — **absorption proven** |
| dx3 | gen | `(code==alpha\|\|prefix==)` | 200 | 1 | `["alpha\|\|prefix=="]` — `)` NOT absorbed (GROUP_END excluded from value) |
| dx4 | gen | `code==alpha\|\|prefix==` | 200 | 1 | `["alpha\|\|prefix=="]` |
| dx5 | seq | `prefix==&&code==plain` | 200 | 1 | `["absorbpfx"]` — seq-side absorption proven |
| dx6 | seq | `code==plain&&prefix==&&code==plain` | 200 | 0 | conjunction `code==plain && prefix=="&&code==plain"`: no row satisfies both |
| rr1–rr7 | – | re-runs of p8, p9, d1, d4, x1, x2, x3 | 500/400/400/400/500/500/500 | – | statuses stable across runs |

## Files

- `<id>-<slug>.json` — raw response body per probe (jq-untouched).
- `probes-log.txt` — every curl command + status/rowcount in execution order.
- `fixture/` — unfiltered listing bodies (plain + `stats=true`, matrix-time and
  post-phase-2), DB prefix/checkDigitAlgo audit, tenant-create response.
- `populate-legacy/` — populate.sh per-step evidence.
