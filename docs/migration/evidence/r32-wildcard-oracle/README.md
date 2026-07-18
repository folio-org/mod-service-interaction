# r32-wildcard-oracle — legacy `%`/`_` wildcard semantics (M9 R32 Phase 1)

Empirical oracle against the BOOTED legacy Grails module answering review №4
finding F-39 (`%` wildcard semantics unregistered; REQ-022 AC1/AC5 falsified).
The review's three observations are re-derived — and the underlying mechanism
turns out to be richer than any of the three summaries: the three lookup paths
(`==`, `=~`, `match`) each treat wildcards differently, and the `=~` path's
behavior is a **broken escape-replacement that injects the literal characters
`$2` into the ILIKE pattern**. Positive proof included. Legacy ground truth
only.

Rig, tenant (`r31o`), fixture and dates are shared with
`../r31-escaped-oracle/` (one oracle session — see its README "Rig
identification" and "Fixture"). Wildcard-discriminating rows: `ab_cd`,
`abXcd`, `ab%cd`, `abcd`, `abXYcd`, `ab\%cd`, later `"ab%cd"`, `a%b`, and the
positive-proof row `ab$2cd`.

## The three-way split (all F-39 observations re-derived)

| Path | `%` | `_` | `\%` / `\_` | Bind evidence |
|---|---|---|---|---|
| `filters` `==`/`!=` | literal | literal | literal `\%`-as-two-chars (raw value) | `ab%cd` bound raw to `=` (mp7) |
| `filters` `=~`/`!~` | **broken**: every `%` with a preceding non-backslash char becomes the literal two characters `$2`; a LEADING `%` stays a live wildcard | live wildcard | escaped-literal `%`/`_` (LIKE ESCAPE `\`) | `%ab$2cd%` (mp1), `%%cd%` (d2), `%%$2cd%` (mp4), `%a$2%b%` (mp5), `%ab\%cd%` (mp3), `%ab_cd%` (mp2) |
| `match`+`term` | live wildcard | live wildcard | escaped-literal | `%ab%cd%` (mp8) |

**The `=~`/`!~` transform, exactly** (consuming-group semantics proven by
mp4 vs mp5): scanning left-to-right non-overlapping, every occurrence of
`<c>%` where `<c>` is any character except `\` is replaced by `<c>$2` —
i.e. regex `([^\\])%` → `$1` + literal `$2` (a botched backreference inside
`SimpleLookupService`); the result is wrapped `%…%` and sent to PostgreSQL
`ILIKE`, where remaining backslashes act as LIKE escapes.

**Positive proof (mp1).** After creating a row whose code is literally
`ab$2cd`, the F-39 reproduction `code=~ab%cd` — which returned `[]` in the
review and in this oracle's main matrix (w1) — returns exactly that row.
The empty result was never "matches nothing by rule"; it was a corrupted
pattern matching a string nobody had stored.

**Reconciliation with the review.** `=~ab%cd → []` re-derived (w1) and
explained above. `match=code&term=ab%cd → 3 rows` in the review is the live
wildcard passthrough over their 3-row fixture; over this bundle's larger
fixture the same probe returns every `ab…cd` row (t1: 6, mp8: 8 as the
fixture grew) — same semantics. `==ab%cd → 1 row` re-derived (w9/mp7,
literal).

## Further pinned edges

| Probe | Decoded | HTTP | Rows | Verdict |
|---|---|---|---|---|
| w2 | `code=~ab_cd` | 200 | 3 | `_` live in `=~` (matches `ab_cd`,`abXcd`,`ab%cd`) |
| w4/w5 | `code=~ab\%cd` / `ab\_cd` | 200 | 1 | `\%`/`\_` = escaped literal |
| d1/d4/d6 | `=~ab%` / `a%b` / `ab\%c%` | 200 | 0 | non-leading `%` → `$2` → no match |
| d2/d3 | `=~%cd` / `%` | 200 | 7 / 47 | leading `%` is a LIVE wildcard |
| w7/d5/mp6 | `code!~…` | 200 | complement | `!~` = NOT ILIKE of the same transformed pattern |
| w8/d8 | `code=="ab%cd"` | 200 | 0 → 1 | quotes literal (row `"ab%cd"` proves) |
| d9/w11 | `code=~"ab%cd"` | 200 | 0 | quoted value + non-leading `%` → `$2`-corrupted like any other |
| d11 | `code=~` (empty RHS) | 200 | 47 | unparseable → whole filter dropped → unfiltered |
| mb1/mb3 | `=~a\b` / `match term=a\b` | 200 | 8 | in ILIKE paths `\b` is a LIKE escape → matches literal `ab`, NOT `a\b` |
| mb2 | `=~a\\b` | 200 | 1 | `\\` = escaped backslash → matches row `a\b` |
| t2/t3/d14 | `match term=ab_cd`/`abcd`/`ab\_cd` | 200 | 3/1/1 | match path: `_` live, `\_` literal |
| t5 | `match=code&match=name&term=ab%cd` | 200 | 6 | multi-property OR, same live semantics |
| t6 | `match=code&term=ab%cd&term=abcd` | **500** | – | multiple `term` params → uncaught server error (`t6-match-multiterm.json`) |
| d12/d13 | `term=%` / `term=` (empty) | 200 | 47 | live-`%`-matches-all; empty term → unfiltered |
| mi1 | `code=i=ab%cd` | 200 | 1 `ab$2cd` | `=i=` is UNWRAPPED ilike with the same broken transform (bind `ab$2cd`) |
| mi2 | `code=i=ab\%cd` | 200 | 1 `ab%cd` | `\%` escaped literal under `=i=` (bind `ab\%cd`) |
| mi3 | `code=i=ab_cd` | 200 | 3 | `_` is a LIVE wildcard even in `=i=` "equality" (bind `ab_cd`) |
| mi4 | `code=i=ABXCD` | 200 | 1 `abXcd` | case-fold control (bind lowercased `abxcd`) |
| r1 | RAW unencoded `filters=code=~ab%cd` | 200 | 0 | `%cd` is a valid URL escape → value decodes to `ab`+0xCD (parse tree `value_exp ab �`); what a naive client actually sends |

## REQ-022 impact (for the R32 Phase 2 SDD session)

REQ-022 AC1/AC5's claim that `%`/`_` are "treated as literal characters, as
legacy does" is FALSE on every path except `==`/`!=`. The true legacy model
the port must replicate under DP-1(a) bug-for-bug parity is the three-way
split table above, including the `$1$2`-literal transform, leading-`%`
liveness, LIKE-escape backslash semantics, and the match-path passthrough.

## Files

- `<id>-<slug>.json` — raw response body per probe.
- `probes-log.txt` — every probe: exact wire query, decoded form, status,
  rows, matched codes; mech probes carry `sql-bind:` lines.
- `sqltrace-excerpt.txt` — Hibernate SQL + bind excerpt for every `ng_code`
  criterion (mech boot).
- Harness scripts and parse trees: `../r31-escaped-oracle/harness/`,
  `../r31-escaped-oracle/fixture/parse-trees.txt` (shared session).
