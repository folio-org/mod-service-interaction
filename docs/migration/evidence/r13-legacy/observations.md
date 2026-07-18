# R13 — legacy oracle observations (re-review №2 verification)

Two independent runs against the real legacy Grails module, each on a
freshly enabled tenant (`r13a` run 1, `r13b` run 2). Every status below was
observed identically in both runs (`diff` of the directed manifests is
empty); raw bodies and response headers are retained per probe under
`run1/` and `run2-directed/`.

## Dashboard create — review finding F-20 confirmed (→ D-27)

| Probe | Legacy observed (×2) | Old harness pin | Port (governed) |
|---|---|---|---|
| `POST /servint/dashboard` | **200** + created body | 201 (wrong) | 201 |

`run1/dashboard-create.json`: `{"id":…,"name":"Migration Board","widgets":[]}`.
The 200 comes from legacy's bespoke `respond` call. D-27 registration +
consumer analysis is R16 scope; populate.sh now pins 200/201 per side.

## Widget-definition POST — F-19 confirmed, D-2's 500 explained (state, not environment)

| Probe | Legacy observed (×2) | Notes |
|---|---|---|
| First `POST /servint/widgets/definitions` (fresh name+version) | **201** + created body | body carries NO id (gson `includes:[]`); row proven by filtered readback (1 row) |
| Identical duplicate POST (`widget-def-create-retry`, `widget-def-create-duplicate`) | **500**, `text/html` | row count stays 1; log shows `ConstraintViolationException` (unique name+version) → error render dies in `NoSuchMessageException` (`ViewException`) |

Resolution of the review's open 201-vs-500 question: the outcome is
**state-dependent, not environment-dependent**. A fresh create answers 201;
only the duplicate-key retry path 500s (while the errors view renders the
unique-constraint message). The old D-2 pin ("legacy 500s while creating
the row") came from an M4 observation of the duplicate path. D-2 must be
re-corrected in R16 to: legacy 201 (create) / 500 (duplicate) vs port 405
(operation never declared).

## F-21 parser rows (`GET /servint/numberGeneratorSequences`, `perPage=100&sort=code;asc`)

| Filter | Legacy observed (×2) | Review's claim | Verdict |
|---|---|---|---|
| `enabled==notabool` | **500** | 500 | confirmed (`ConversionFailedException`/`NumberFormatException` in log) |
| `nextValue==` (empty RHS) | **200, all 11 rows** | 200, all 12 | confirmed — clause silently dropped, full listing returned (row-count differs only by tenant contents) |
| `code==pl\ain` | **200, 0 rows** | 200, 0 | confirmed — backslash treated as literal, no unescaping |
| `(code==plain` | **500** | 500 | confirmed |
| `nextValue>notanumber` | **500** | 500 | confirmed |
| 10 000-char filter value | **400, empty body, no Content-Type** | 400 empty | confirmed (container-level URI/header limit) |
| `code==plain` (baseline) | **200, 1 row** | — | sanity baseline for the port comparison |

Legacy 500 envelope (all rows above):
`{"error":500,"timestamp":"…","message":"Uncaught Internal server error"}`
(101 bytes, `application/json`).

## Starred-legacy f14 rows of probes.tsv — now measured

| Probe | Legacy observed (×2) | Port pin (probes.tsv) |
|---|---|---|
| f14-malformed-json | **500** (`JSONException` in log) | 400 |
| f14-missing-tenant | **500** (`TenantNotFoundException`) | 400 |
| f14-stats-notabool | **200**, full listing | 200 |
| f14-perpage-nonnumeric | **200**, full listing | 200 |
| f14-duplicate-code | **500** (rolled back — listing unchanged) | 409 (D-24) |

R16 pins these into the `expect_legacy` column.

## Bonus finding — live evidence for D-5 (tenant-blind legacy key cache)

After run 1 (`r13a`) had created its signing key, run 2's
`GET /servint/attestation/token` on tenant `r13b` answered 200 **without
writing any row to `r13b…db_key_pair`** (count stayed 0 while
`r13a…db_key_pair` = 1): the legacy module-level key cache is tenant-blind
and signed `r13b`'s token with `r13a`'s key. After a JVM restart (cache
cleared) the same request created `r13b`'s own key row. This is the
cross-tenant leak D-5 documents and the port deliberately fixes
(tenant-keyed cache). Fixture note: `r13b-populated-schema.sql` was dumped
after the post-restart call, so it carries its own `db_key_pair` row.

## Tenant enable (legacy `_tenant` 1.2)

`POST /_/tenant` body `{"module_to":…,"parameters":[loadReference,
loadSample=true]}` → **201**, `text/html`, 2-byte body. Seeded state:
9 number generators (8 seeded + m4gen), 11 sequences, 4 refdata categories /
15 values, 3 app settings (1 seeded + 2 created).
