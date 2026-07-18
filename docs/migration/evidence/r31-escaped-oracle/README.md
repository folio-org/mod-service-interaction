# r31-escaped-oracle — legacy escaped-token / structural-boundary semantics (M9 R31 Phase 1)

Empirical oracle against the BOOTED legacy Grails module answering review №4
finding F-38 (escaped logical tokens bypass R22 absorption; port drops the
filter → unfiltered listing). Matrix-driven per remediation plan 4 §R31: the
review's two reproductions re-derived plus escaped-mid-expression, multiple
escapes, quoted values, escaped/unescaped `!`/`(`/`)`, lone `&`/`|`, and
escape-plus-real-operator combinations — every probe backed by directed
fixture rows so the matched codes disambiguate the parse. Legacy ground truth
only; no conclusions here about what the port should do.

## Rig identification

| Item | Value |
|---|---|
| Legacy artifact | `service/build/libs/mod-service-interaction-4.4.0-SNAPSHOT.jar` |
| Jar sha256 | `abb3721190394d2eea3e5661345b4d0fd8da89add558e0fc9924b26ec79b4c57` (identical to the R13/R22/R-CERT rig) |
| Boot | `java -Ddb.host=localhost -Ddb.port=54321 -Ddb.database=okapi_modules_test -Ddb.username=folio_admin -Ddb.password=folio_admin -Dserver.port=8080 -jar <jar>` (JDK 17). Re-booted once mid-oracle with `-Dlogging.level.org.hibernate.SQL=DEBUG -Dlogging.level.org.hibernate.type=TRACE -Dlogging.level.com.k_int.web.toolkit=TRACE` added for the mech phase (same jar, db, tenant). |
| Database | fresh `testing_pg` (postgres:18, host :54321), db `okapi_modules_test` — prior rig containers were lost to host cleanup; rebuilt from `tools/testing/docker-compose.yml` |
| Filter engine | `com.k_int.grails:web-toolkit-ce:10.6.4` — `SimpleLookupService` + ANTLR grammar `SimpleLookupWtk.g4` |
| Tenant | `r31o`, fresh: `POST /_/tenant` body `{"module_to":"mod-service-interaction-4.4.0-SNAPSHOT","parameters":[{"key":"loadReference","value":"true"}]}` → 201 |
| Shared with | `../r32-wildcard-oracle/` — one rig session, one tenant, one fixture (plan §R31 "shares its oracle session with R32 Phase 1") |
| Date | 2026-07-23 |

## Fixture (directed rows)

`harness/oracle.py` Phase A creates, via REST (`POST /servint/numberGenerators`,
each `{"code":C,"name":C}`, all 201 — statuses in `probes-log.txt`), one row per
plausible literal interpretation of every probe: raw-with-backslash
(`alpha\&&prefix==`), unescaped (`alpha&&prefix==`), single-char
(`alpha&prefix==`) variants and so on — 34 rows over the 8 `loadReference`
seeds (baseline 42). Phase D adds quoted-literal and backslash-family rows
(`"alpha&&beta"`, `a\b`, `a\\b`, …; baseline 47); the mech phase adds the
`ab$2cd` positive-proof row (48). Which row a probe returns IS the parse
verdict.

## Mechanism (parse-tree-derived, row-confirmed, bind-confirmed)

Legacy logs its ANTLR parse tree per request (`fixture/parse-trees.txt`) and,
in the mech re-boot, the exact SQL bind (`../r32-wildcard-oracle/sqltrace-excerpt.txt`):

1. **The backslash acts at the LEXER, and is RETAINED in the value.**
   `code==alpha\&&prefix==` lexes `\&` as one escaped token, leaving a single
   `&` — two adjacent unescaped chars never form, so no structural `&&`
   exists; the whole remainder is one `value_exp` (parse tree:
   `(value_exp alpha \& & prefix ==)`). The criterion value is the RAW source
   text **backslash included**: the probe matches the row whose code is
   literally `alpha\&&prefix==` (e1; sql-bind `alpha\&&prefix==`, mb4).
   Legacy NEVER unescapes: `a\b` matches only row `a\b` (db1/mb5), `a\\b`
   only `a\\b` (db2).
2. **A later REAL structural token still splits.**
   `code==alpha\&&x&&code==alpha` parses as a genuine AND of two comparisons
   (values `alpha\&&x` and `alpha`) — 0 rows; the `||` variant returns both
   rows (m1/m2). "Absorption" is therefore not a separate pass but the
   grammar's greedy `value_exp` bounded by the next UNESCAPED structural
   token.
3. **Quote characters are ordinary literal value text.** `code=="alpha&&beta"`
   matches nothing until a row whose code contains the quotes exists — then
   it matches exactly that row (q2 → 0; dq1 → `"alpha&&beta"`). Quotes have
   no grouping or escaping power.
4. **Unescaped `!` / `(` / `)` inside a value break the parse and legacy
   DROPS THE WHOLE FILTER** — unfiltered listing (x4/x5/x6: flat error parse
   tree `(standard_expr code == alpha ! x)`, 42 rows). Escaped forms
   (`\!`/`\(`/`\)`) are raw literal value text like every other escape
   (x1–x3).
5. **Lone `&` / lone `|` are legal value characters** (l1/l2 exact match).

## Results

Baselines: matrix 42, Phase D 47, mech 48. Full row-by-row record in
`probes-log.txt`; response bodies verbatim per probe.

| Probe | Decoded filter | HTTP | Rows | Verdict |
|---|---|---|---|---|
| c1 | *(unfiltered)* | 200 | 42 | baseline |
| c2 | `code==alpha` | 200 | 1 `alpha` | control |
| c3 | `code==alpha&&prefix==` | 200 | 1 `alpha&&prefix==` | R22 dx2 re-confirmed |
| c4 | `code==alpha\|\|prefix==` | 200 | 1 `alpha\|\|prefix==` | R22 dx4 re-confirmed |
| c5 | `code==alpha&&code==alpha` | 200 | 1 `alpha` | valid compound works |
| e1 | `code==alpha\&&prefix==` | 200 | 1 `alpha\&&prefix==` | **F-38 row 1: raw literal, backslash retained** |
| e2 | `code==alpha\\|\|prefix==` | 200 | 1 `alpha\\|\|prefix==` | **F-38 row 2: same** |
| e3 | `code==alpha\&&beta` | 200 | 1 `alpha\&&beta` | escaped mid-expression, non-empty RHS: raw |
| e4 | `code==alpha\\|\|beta` | 200 | 1 `alpha\\|\|beta` | same |
| e5 | `code==alpha\&&beta\\|\|gamma` | 200 | 1 `alpha\&&beta\\|\|gamma` | multiple escapes: raw |
| q1 | `code=="alpha\&&beta"` | 200 | 0 | quotes+escape literal; no such row |
| q2 | `code=="alpha&&beta"` | 200 | 0 | quotes literal (row `alpha&&beta` NOT matched) |
| dq1 | `code=="alpha&&beta"` (after quoted row) | 200 | 1 `"alpha&&beta"` | **quotes-are-literal proven** |
| dq2 | `code=="alpha\&&beta"` | 200 | 0 | no unescaping inside quotes either |
| x1–x3 | `code==alpha\!x` / `\(x` / `\)x` | 200 | 1 raw each | escaped specials: raw literal |
| x4–x6 | `code==alpha!x` / `(x` / `)x` | 200 | 42 | **parse error → filter dropped → unfiltered** |
| l1/l2 | `code==a&b` / `code==a\|b` | 200 | 1 each | lone `&`/`\|` legal in value |
| m1 | `code==alpha\&&x&&code==alpha` | 200 | 0 | real `&&` splits: AND of two eqs |
| m2 | `code==alpha\&&x\|\|code==alpha` | 200 | 2 | real `\|\|` splits: OR matches both rows |
| db1/db2 | `code==a\b` / `code==a\\b` | 200 | 1 raw each | backslash before non-special / double backslash: raw |
| mb4/mb5 | e1 / db1 with SQL trace | 200 | 1 | binds `alpha\&&prefix==`, `a\b` — raw equality |

### Phases S / S2 / S3 — operator tokens inside values (supplementary)

Run after the port-fix design surfaced unprobed shapes; directed rows
`alpha==`, `alpha==beta`, `alpha!=beta`, … created per probe (creates in
`probes-log.txt`).

| Probe | Decoded filter | HTTP | Rows | Verdict |
|---|---|---|---|---|
| s1/s2 | `code==alpha==` / `alpha=~` | 200 | 1 raw each | trailing `==`/`=~` absorb into the value |
| s3 | `code==alpha<` | 200 | 55 | bare trailing `<` voids the filter → unfiltered |
| s4/s5 | `code==alpha==beta` / `alpha=~beta` | 200 | 1 raw each | mid `==`/`=~` absorb |
| s6 | `code==alpha<beta` | 400 | – | re-shapes: `(value_exp code == alpha) < beta` — `beta` becomes the SUBJECT → `Invalid property: beta` |
| s7 | `code=~alpha==` | 200 | 2 | absorption applies under `=~` too (contains) |
| s8 | `code==alpha>5` | 500 | – | non-identifier tail after single `>` → uncaught 500 |
| s9 | `code==alpha=beta` | 400 | – | single `=` re-shapes like s6 |
| s10-s13 | `alpha!=beta` / `!~` / `=i=` / `<=` | 200 | 1 raw each | `!=` `!~` `=i=` `<=` all absorb |
| s14 | `code==alpha"beta` | 200 | 1 raw | mid-value quote absorbs |
| s15/s16 | `alpha>=beta` / `alpha<>beta` | 200 | 1 raw each | `>=` `<>` absorb |
| s17 | `code==alpha\&&prefix==&&code==alpha` | 200 | 0 | **compound preference**: parses as AND of `code==(alpha\&&prefix==)` and `code==alpha` — the planted whole-absorb bait row does NOT match |

**F-38 verdict.** The review's two reproductions are re-derived exactly
(legacy = one row, the raw literal). The plan's working hypothesis
("absorption generalizes to raw-text semantics") is CONFIRMED and sharpened:
the value is the raw source text between the comparison operator and the next
unescaped structural token (or EOF), backslashes and quotes included, with no
unescaping at any point; the operator spellings `== =~ =i= != !~ <= >= <>`
are legal inside values while single `=`/`<`/`>` re-shape the expression
(identifier tail = subject → 400) and unescaped `!`/`(`/`)` void the entire
filter; a real structural token whose right side stands alone always splits
(s17).

## Files

- `<id>-<slug>.json` — raw response body per probe.
- `probes-log.txt` — fixture creates + every probe (exact wire query, decoded
  form, status, rows, matched codes; mech probes carry `sql-bind:` lines).
- `fixture/` — post-create baselines, all ANTLR parse trees
  (`parse-trees.txt`).
- `harness/oracle.py`, `harness/oracle-dx.py`, `harness/oracle-mech.py`,
  `harness/oracle-s.py`, `harness/oracle-s2.py` — the exact probe drivers
  (fixture creation + matrix + disambiguation + SQL-bind capture +
  operator-in-value supplements; Phase S3 and the `=i=` Phase M2 ran as
  inline drivers recorded verbatim in `probes-log.txt`).
